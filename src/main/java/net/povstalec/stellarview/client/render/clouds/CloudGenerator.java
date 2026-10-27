package net.povstalec.stellarview.client.render.clouds;

import net.povstalec.stellarview.StellarView;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds cloud boxes off the render thread.
 * <p>
 * Hand-off contract:
 * <ul>
 * <li>One dedicated daemon thread. At most one job exists at any time (queued or running):
 * the render thread only submits when the previous job has reported back.</li>
 * <li>A job reads only its immutable {@link CloudRequest} and writes only a brand new {@link CloudMesh},
 * which it publishes through an {@link AtomicReference} (a volatile write). The render thread takes it with a
 * volatile read, so everything the worker wrote is visible to it. The worker never touches the mesh again.</li>
 * <li>Every job publishes exactly one mesh, even when it is abandoned or throws (finally block).</li>
 * <li>Cancelling is a volatile epoch increment. A running job checks it between groups and stops early.
 * Nobody ever waits for anybody: there are no locks, joins or futures on the render thread.</li>
 * <li>Results whose epoch is outdated (old world, resource reload) are dropped by {@link #poll()}.</li>
 * </ul>
 * All methods except the job itself must be called on the render thread.
 */
final class CloudGenerator
{
	/** Cells per group side. Groups are the unit of culling and of draw batching */
	static final int GROUP_SIZE = 16;

	/** How strongly the per-cell random value roughens the cloud outlines (in density units) */
	static final float EDGE_FUZZ = 0.16F;

	/**
	 * Density threshold that leaves the given fraction of cells visible, for fractions 0, 0.05, 0.10 ... 1.
	 * Measured from the distribution of (density - EDGE_FUZZ * random) of this generator over 120 000 random cells.
	 */
	private static final float[] COVERAGE_THRESHOLDS = {
			0.80F, 0.668F, 0.615F, 0.578F, 0.548F, 0.524F, 0.500F, 0.479F, 0.459F, 0.439F, 0.419F,
			0.400F, 0.381F, 0.360F, 0.339F, 0.317F, 0.291F, 0.261F, 0.224F, 0.170F, 0.05F
	};

	/** Largest visible fraction of cells the renderer ever asks for (full thunderstorm) */
	static final float MAX_COVERAGE = 0.9F;

	/** Cells below this value can never become visible, so they are not stored */
	private static final float MIN_STORED_KEY = thresholdForCoverage(MAX_COVERAGE) - 0.005F;

	private static final long SALT_WARP_X = 0x1F83D9ABFB41BD6BL;
	private static final long SALT_WARP_Z = 0x5BE0CD19137E2179L;
	private static final long SALT_SHAPE = 0x6A09E667F3BCC908L;
	private static final long SALT_REGION = 0x3C6EF372FE94F82BL;
	private static final long SALT_RANDOM = 0xA54FF53A5F1D36F1L;
	private static final long SALT_JITTER_X = 0x510E527FADE682D1L;
	private static final long SALT_JITTER_Z = 0x9B05688C2B3E6C1FL;

	/** Wavelength of the clear-sky / cloudy region noise, relative to the cloud shape size */
	private static final double REGION_SCALE = 8.0;
	/** Fraction of the cell pitch a box may be moved away from its cell centre */
	private static final double JITTER = 0.9;

	private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean(false);

	private final AtomicReference<CloudMesh> finished = new AtomicReference<>();
	/** Written only by the render thread, read by the worker */
	private volatile int epoch = 0;

	// Render thread only
	private ExecutorService executor;
	private long nextId = 1;
	private CloudRequest inFlight = null;

	/**
	 * @return The visible fraction of cells mapped to a density threshold
	 */
	static float thresholdForCoverage(float coverage)
	{
		float position = Math.max(0.0F, Math.min(1.0F, coverage)) * (COVERAGE_THRESHOLDS.length - 1);
		int index = Math.min((int) position, COVERAGE_THRESHOLDS.length - 2);
		float fraction = position - index;
		return COVERAGE_THRESHOLDS[index] + (COVERAGE_THRESHOLDS[index + 1] - COVERAGE_THRESHOLDS[index]) * fraction;
	}

	int epoch()
	{
		return epoch;
	}

	long nextId()
	{
		return nextId++;
	}

	/**
	 * @return The job that is queued or running, or null
	 */
	CloudRequest inFlight()
	{
		return inFlight;
	}

	/**
	 * Makes every queued, running or finished job stale. Never blocks.
	 */
	void invalidate()
	{
		epoch++; // Single writer (the render thread), so the read-modify-write cannot lose updates
	}

	/**
	 * Starts a job. Does nothing if a job is still in flight.
	 * @return True if the job was accepted
	 */
	boolean submit(CloudRequest request)
	{
		if(inFlight != null)
			return false;

		if(executor == null)
		{
			executor = Executors.newSingleThreadExecutor(runnable ->
			{
				Thread thread = new Thread(runnable, "Stellar View cloud generator");
				thread.setDaemon(true);
				thread.setPriority(Thread.MIN_PRIORITY + 1);
				return thread;
			});
		}

		try
		{
			executor.execute(() -> run(request));
			inFlight = request;
			return true;
		}
		catch(RejectedExecutionException e)
		{
			return false;
		}
	}

	/**
	 * Takes a finished job if there is one.
	 * @return A complete mesh of the current epoch, a failed mesh, or null (nothing new, or a stale result)
	 */
	CloudMesh poll()
	{
		CloudMesh mesh = finished.getAndSet(null);
		if(mesh == null)
			return null;

		// Every job reports exactly once and only one job exists at a time, so this result belongs to the job in flight
		if(inFlight != null && inFlight.id() == mesh.request.id())
			inFlight = null;

		if(mesh.failed)
			return mesh;

		if(!mesh.complete || mesh.request.epoch() != epoch)
			return null;

		return mesh;
	}

	//============================================================================================
	//*****************************************Worker side****************************************
	//============================================================================================

	private void run(CloudRequest request)
	{
		CloudMesh mesh = null;
		try
		{
			mesh = build(request);
		}
		catch(Throwable e)
		{
			if(FAILURE_LOGGED.compareAndSet(false, true))
				StellarView.LOGGER.error("Stellar View cloud generation failed, clouds are disabled for this session", e);
			mesh = CloudMesh.failed(request);
		}
		finally
		{
			if(mesh == null)
				mesh = CloudMesh.failed(request);

			finished.set(mesh);
		}
	}

	private CloudMesh build(CloudRequest request)
	{
		final int groupCells = GROUP_SIZE * GROUP_SIZE;
		final int maxGroups = request.groupsX() * request.groupsZ();
		final double pitch = request.pitch();
		final double originX = request.originX();
		final double originZ = request.originZ();
		final double groupSpan = GROUP_SIZE * pitch;
		final double inverseScale = 1.0 / request.featureScale();
		final double inverseRegionScale = inverseScale / REGION_SCALE;
		final float boundsPadding = request.boxHalfWidth() + (float) (pitch * JITTER * 0.5);
		final double keepDistance = request.extent() + groupSpan * 0.7072; // Circle filter: group centre to window centre

		final long seed = request.seed();
		final long seedWarpX = seed ^ SALT_WARP_X;
		final long seedWarpZ = seed ^ SALT_WARP_Z;
		final long seedShape = seed ^ SALT_SHAPE;
		final long seedRegion = seed ^ SALT_REGION;
		final long seedRandom = seed ^ SALT_RANDOM;
		final long seedJitterX = seed ^ SALT_JITTER_X;
		final long seedJitterZ = seed ^ SALT_JITTER_Z;

		float[] boxes = new float[maxGroups * groupCells * CloudMesh.FLOATS_PER_BOX];
		int[] groupStart = new int[maxGroups];
		int[] groupSize = new int[maxGroups];
		float[] groupBounds = new float[maxGroups * CloudMesh.FLOATS_PER_BOUNDS];

		int boxCount = 0;
		int groupCount = 0;

		// Rows of groups along Z, groups along X inside a row: neighbouring groups in the buffer are neighbours in the sky
		for(int groupZ = 0; groupZ < request.groupsZ(); groupZ++)
		{
			for(int groupX = 0; groupX < request.groupsX(); groupX++)
			{
				if(request.epoch() != epoch)
					return CloudMesh.abandoned(request);

				long firstCellX = request.cellMinX() + (long) groupX * GROUP_SIZE;
				long firstCellZ = request.cellMinZ() + (long) groupZ * GROUP_SIZE;

				double groupCentreX = (firstCellX + GROUP_SIZE * 0.5) * pitch;
				double groupCentreZ = (firstCellZ + GROUP_SIZE * 0.5) * pitch;
				double toCentreX = groupCentreX - request.centreX();
				double toCentreZ = groupCentreZ - request.centreZ();
				if(toCentreX * toCentreX + toCentreZ * toCentreZ > keepDistance * keepDistance)
					continue;

				int start = boxCount;
				float minX = Float.MAX_VALUE;
				float minZ = Float.MAX_VALUE;
				float maxX = -Float.MAX_VALUE;
				float maxZ = -Float.MAX_VALUE;

				for(int cellZ = 0; cellZ < GROUP_SIZE; cellZ++)
				{
					long indexZ = firstCellZ + cellZ;
					for(int cellX = 0; cellX < GROUP_SIZE; cellX++)
					{
						long indexX = firstCellX + cellX;

						double worldX = indexX * pitch;
						double worldZ = indexZ * pitch;

						float density = density(worldX, worldZ, inverseScale, inverseRegionScale, seedWarpX, seedWarpZ, seedShape, seedRegion);
						float random = (float) CloudNoise.toUnit(CloudNoise.hash(seedRandom, indexX, indexZ));

						if(density - EDGE_FUZZ * random < MIN_STORED_KEY)
							continue;

						double jitterX = (CloudNoise.toUnit(CloudNoise.hash(seedJitterX, indexX, indexZ)) - 0.5) * JITTER * pitch;
						double jitterZ = (CloudNoise.toUnit(CloudNoise.hash(seedJitterZ, indexX, indexZ)) - 0.5) * JITTER * pitch;

						float x = (float) (worldX + jitterX - originX);
						float z = (float) (worldZ + jitterZ - originZ);

						int offset = boxCount * CloudMesh.FLOATS_PER_BOX;
						boxes[offset] = x;
						boxes[offset + 1] = z;
						boxes[offset + 2] = density;
						boxes[offset + 3] = random;
						boxCount++;

						minX = Math.min(minX, x);
						minZ = Math.min(minZ, z);
						maxX = Math.max(maxX, x);
						maxZ = Math.max(maxZ, z);
					}
				}

				if(boxCount == start)
					continue;

				groupStart[groupCount] = start;
				groupSize[groupCount] = boxCount - start;
				int bounds = groupCount * CloudMesh.FLOATS_PER_BOUNDS;
				groupBounds[bounds] = minX - boundsPadding;
				groupBounds[bounds + 1] = minZ - boundsPadding;
				groupBounds[bounds + 2] = maxX + boundsPadding;
				groupBounds[bounds + 3] = maxZ + boundsPadding;
				groupCount++;
			}
		}

		return new CloudMesh(request, Arrays.copyOf(boxes, boxCount * CloudMesh.FLOATS_PER_BOX), boxCount,
				groupCount, Arrays.copyOf(groupStart, groupCount), Arrays.copyOf(groupSize, groupCount),
				Arrays.copyOf(groupBounds, groupCount * CloudMesh.FLOATS_PER_BOUNDS));
	}

	/**
	 * Cloud density of a cell, roughly in [0, 1] with a mean of 0.5.
	 * A lightly domain warped fractal noise gives the cloud shapes, a much larger noise gives clear and cloudy regions.
	 */
	private static float density(double worldX, double worldZ, double inverseScale, double inverseRegionScale,
								 long seedWarpX, long seedWarpZ, long seedShape, long seedRegion)
	{
		double x = worldX * inverseScale;
		double z = worldZ * inverseScale;

		double warpedX = x + 0.45 * CloudNoise.gradient(seedWarpX, x * 0.5, z * 0.5);
		double warpedZ = z + 0.45 * CloudNoise.gradient(seedWarpZ, x * 0.5 + 31.7, z * 0.5);

		double shape = CloudNoise.fractal(seedShape, warpedX, warpedZ, 4);
		double region = CloudNoise.gradient(seedRegion, worldX * inverseRegionScale, worldZ * inverseRegionScale);

		return (float) (0.5 + 0.6 * shape + 0.25 * region);
	}
}
