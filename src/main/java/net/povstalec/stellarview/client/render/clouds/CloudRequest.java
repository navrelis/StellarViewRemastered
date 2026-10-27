package net.povstalec.stellarview.client.render.clouds;

/**
 * Immutable description of one generation job. The worker reads nothing but this record.
 * <p>
 * Coordinates are in cloud space (world X/Z plus the accumulated wind drift).
 * The window is a rectangle of whole groups on an absolute group grid, so a group always covers the same cells.
 *
 * @param id			Unique, increasing job id (render thread counter)
 * @param epoch			Generator epoch at submission. A job whose epoch is outdated is stale and gets discarded
 * @param seed			Noise seed of the world and dimension
 * @param pitch			Cell spacing in blocks
 * @param featureScale	Size of the cloud shapes in blocks (noise wavelength of the base octave)
 * @param boxHalfWidth	Largest half width a box can reach, used for the group bounds
 * @param cellMinX		Absolute cell index of the window's first column (a multiple of the group size)
 * @param cellMinZ		Absolute cell index of the window's first row (a multiple of the group size)
 * @param groupsX		Window width in groups
 * @param groupsZ		Window depth in groups
 * @param centreX		Cloud space X of the camera when the job was made
 * @param centreZ		Cloud space Z of the camera when the job was made
 * @param reach			Cloud radius in blocks that was asked for (used to detect changes)
 * @param visibleReach	Cloud radius in blocks the window really covers (smaller than reach if the cell cap applied)
 * @param extent		Half size of the generated area around the centre (reach plus margin plus box size)
 * @param margin		How far the camera may move (in cloud space) before a new window is needed
 */
record CloudRequest(long id, int epoch, long seed, double pitch, double featureScale, float boxHalfWidth,
					long cellMinX, long cellMinZ, int groupsX, int groupsZ,
					double centreX, double centreZ, double reach, double visibleReach, double extent, double margin)
{
	/**
	 * @return Cloud space X of the window origin. Instance positions are stored relative to it
	 */
	double originX()
	{
		return cellMinX * pitch;
	}

	/**
	 * @return Cloud space Z of the window origin. Instance positions are stored relative to it
	 */
	double originZ()
	{
		return cellMinZ * pitch;
	}

	/**
	 * @return True if a window made with these shape parameters can be reused as it is
	 */
	boolean sameShape(long seed, double pitch, double reach)
	{
		return this.seed == seed && this.pitch == pitch && this.reach == reach;
	}

	/**
	 * @return True if the camera is still inside the area this window was generated for
	 */
	boolean covers(double cloudX, double cloudZ)
	{
		return Math.abs(cloudX - centreX) <= margin && Math.abs(cloudZ - centreZ) <= margin;
	}
}
