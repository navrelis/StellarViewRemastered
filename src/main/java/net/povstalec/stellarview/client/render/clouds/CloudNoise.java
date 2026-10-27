package net.povstalec.stellarview.client.render.clouds;

/**
 * Stateless, seedable 2D noise used by the cloud generator.
 * <p>
 * Gradient noise on an integer lattice with hashed gradients (16 directions) and a quintic fade.
 * Everything is a pure function of its arguments, so it is safe to call from any thread.
 * Lattice coordinates are longs, so the noise stays precise far away from the world origin.
 */
final class CloudNoise
{
	private static final int DIRECTIONS = 16;
	private static final double[] GRADIENT_X = new double[DIRECTIONS];
	private static final double[] GRADIENT_Z = new double[DIRECTIONS];

	/** Scales the raw gradient noise to roughly [-1, 1] */
	private static final double AMPLITUDE = 1.5;

	private static final long OCTAVE_SEED_STEP = 0x632BE59BD9B4E019L;

	static
	{
		for(int i = 0; i < DIRECTIONS; i++)
		{
			double angle = (i + 0.5) * Math.PI * 2.0 / DIRECTIONS;
			GRADIENT_X[i] = Math.cos(angle);
			GRADIENT_Z[i] = Math.sin(angle);
		}
	}

	private CloudNoise() {}

	/**
	 * 64 bit hash of a seed and a lattice point (SplitMix64 finaliser over a weighted sum)
	 */
	static long hash(long seed, long x, long z)
	{
		long h = seed + x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		return h ^ (h >>> 31);
	}

	/**
	 * @return The top 53 bits of the hash as a double in [0, 1)
	 */
	static double toUnit(long hash)
	{
		return (hash >>> 11) * 0x1.0p-53;
	}

	/**
	 * Seeded 64 bit hash of a string (FNV-1a over the UTF-16 code units)
	 */
	static long hashString(String text)
	{
		long h = 0xCBF29CE484222325L;
		for(int i = 0; i < text.length(); i++)
		{
			h ^= text.charAt(i);
			h *= 0x100000001B3L;
		}
		return hash(h, text.length(), 0x5DEECE66DL);
	}

	/**
	 * @return Gradient noise at (x, z), roughly in [-1, 1]
	 */
	static double gradient(long seed, double x, double z)
	{
		double floorX = Math.floor(x);
		double floorZ = Math.floor(z);
		long x0 = (long) floorX;
		long z0 = (long) floorZ;
		double dx = x - floorX;
		double dz = z - floorZ;

		double u = fade(dx);
		double v = fade(dz);

		double a = corner(seed, x0, z0, dx, dz);
		double b = corner(seed, x0 + 1, z0, dx - 1.0, dz);
		double c = corner(seed, x0, z0 + 1, dx, dz - 1.0);
		double d = corner(seed, x0 + 1, z0 + 1, dx - 1.0, dz - 1.0);

		double ab = a + u * (b - a);
		double cd = c + u * (d - c);
		return (ab + v * (cd - ab)) * AMPLITUDE;
	}

	/**
	 * Fractal sum of gradient noise. Each octave is rotated against the previous one to hide lattice artefacts.
	 * @return Noise roughly in [-1, 1]
	 */
	static double fractal(long seed, double x, double z, int octaves)
	{
		double sum = 0.0;
		double amplitude = 1.0;
		double total = 0.0;

		for(int octave = 0; octave < octaves; octave++)
		{
			sum += amplitude * gradient(seed + octave * OCTAVE_SEED_STEP, x, z);
			total += amplitude;
			amplitude *= 0.5;

			double nextX = (x * 0.8 - z * 0.6) * 2.03 + 17.3;
			z = (x * 0.6 + z * 0.8) * 2.03 - 9.1;
			x = nextX;
		}

		return sum / total;
	}

	private static double corner(long seed, long x, long z, double dx, double dz)
	{
		int direction = (int) (hash(seed, x, z) >>> 60);
		return GRADIENT_X[direction] * dx + GRADIENT_Z[direction] * dz;
	}

	private static double fade(double t)
	{
		return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
	}
}
