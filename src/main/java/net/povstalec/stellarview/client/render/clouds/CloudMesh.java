package net.povstalec.stellarview.client.render.clouds;

/**
 * Immutable result of one generation job: the cloud boxes of a window, grouped for culling.
 * <p>
 * The worker creates a new instance for every job and never touches it again after publishing it,
 * so the render thread can read it without any locking.
 * Arrays are never modified after construction.
 */
final class CloudMesh
{
	/** Floats per box: x, z (relative to the window origin), density, random value */
	static final int FLOATS_PER_BOX = 4;
	static final int BYTES_PER_BOX = FLOATS_PER_BOX * Float.BYTES;

	/** Floats per group bounds: min x, min z, max x, max z (relative to the window origin) */
	static final int FLOATS_PER_BOUNDS = 4;

	final CloudRequest request;
	/** False if the job was abandoned (stale) or failed */
	final boolean complete;
	/** True if the job threw an exception */
	final boolean failed;

	final float[] boxes;
	final int boxCount;

	final int groupCount;
	final int[] groupStart;
	final int[] groupSize;
	final float[] groupBounds;

	CloudMesh(CloudRequest request, float[] boxes, int boxCount, int groupCount, int[] groupStart, int[] groupSize, float[] groupBounds)
	{
		this.request = request;
		this.complete = true;
		this.failed = false;
		this.boxes = boxes;
		this.boxCount = boxCount;
		this.groupCount = groupCount;
		this.groupStart = groupStart;
		this.groupSize = groupSize;
		this.groupBounds = groupBounds;
	}

	private CloudMesh(CloudRequest request, boolean failed)
	{
		this.request = request;
		this.complete = false;
		this.failed = failed;
		this.boxes = new float[0];
		this.boxCount = 0;
		this.groupCount = 0;
		this.groupStart = new int[0];
		this.groupSize = new int[0];
		this.groupBounds = new float[0];
	}

	/**
	 * Marks the end of a job that was abandoned because it became stale
	 */
	static CloudMesh abandoned(CloudRequest request)
	{
		return new CloudMesh(request, false);
	}

	/**
	 * Marks the end of a job that threw an exception
	 */
	static CloudMesh failed(CloudRequest request)
	{
		return new CloudMesh(request, true);
	}
}
