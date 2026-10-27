package net.povstalec.stellarview.client.render.clouds;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferUploader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Saves every piece of OpenGL state the cloud renderer changes and puts it back afterwards.
 * <p>
 * Values are read from the driver once per drawn frame, then restored through {@link GlStateManager},
 * so Minecraft's state cache (and every mod that tracks state through it) ends up exactly where it started.
 * State the renderer never touches (viewport, scissor, stencil, polygon offset, logic op, clear values,
 * read framebuffer, cull face mode, front face) is not saved.
 * <p>
 * Render thread only. Not reentrant.
 */
final class GlStateSnapshot
{
	/** Texture unit the cloud renderer samples from */
	static final int TEXTURE_UNIT = GL13.GL_TEXTURE0;

	private final ByteBuffer colorMask = BufferUtils.createByteBuffer(16);

	private int drawFramebuffer;
	private int vertexArray;
	private int arrayBuffer;
	private int program;

	private int activeTexture;
	private int texture;

	private boolean blend;
	private int blendSrcRgb;
	private int blendDstRgb;
	private int blendSrcAlpha;
	private int blendDstAlpha;
	private int blendEquationRgb;
	private int blendEquationAlpha;

	private boolean depthTest;
	private int depthFunc;
	private boolean depthMask;

	private boolean cull;

	private boolean maskRed;
	private boolean maskGreen;
	private boolean maskBlue;
	private boolean maskAlpha;

	private boolean captured = false;

	/**
	 * Reads the current state. Also makes {@link #TEXTURE_UNIT} the active texture unit.
	 */
	void capture()
	{
		drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
		arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
		program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

		blend = GL11.glIsEnabled(GL11.GL_BLEND);
		blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
		blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
		blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
		blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
		blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
		blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

		depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

		cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

		GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
		maskRed = colorMask.get(0) != 0;
		maskGreen = colorMask.get(1) != 0;
		maskBlue = colorMask.get(2) != 0;
		maskAlpha = colorMask.get(3) != 0;

		// The active unit is cached by GlStateManager, the binding on our unit is read from the driver
		activeTexture = GlStateManager._getActiveTexture();
		GlStateManager._activeTexture(TEXTURE_UNIT);
		texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

		captured = true;
	}

	/**
	 * @return True if the blend equation is not the additive one the cloud passes need
	 */
	boolean needsBlendEquationChange()
	{
		return blendEquationRgb != GL14.GL_FUNC_ADD || blendEquationAlpha != GL14.GL_FUNC_ADD;
	}

	/**
	 * Puts back everything {@link #capture()} saved. Safe to call when nothing was captured.
	 */
	void restore()
	{
		if(!captured)
			return;

		captured = false;

		GlStateManager._activeTexture(TEXTURE_UNIT);
		GlStateManager._bindTexture(texture);
		GlStateManager._activeTexture(activeTexture);

		GlStateManager._glUseProgram(program);
		GlStateManager._glBindVertexArray(vertexArray);
		GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
		// Vanilla's vertex buffer cache must not assume its last buffer is still bound
		BufferUploader.invalidate();

		if(blend)
			GlStateManager._enableBlend();
		else
			GlStateManager._disableBlend();
		GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
		if(needsBlendEquationChange())
			GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);

		if(depthTest)
			GlStateManager._enableDepthTest();
		else
			GlStateManager._disableDepthTest();
		GlStateManager._depthFunc(depthFunc);
		GlStateManager._depthMask(depthMask);

		if(cull)
			GlStateManager._enableCull();
		else
			GlStateManager._disableCull();

		GlStateManager._colorMask(maskRed, maskGreen, maskBlue, maskAlpha);
	}
}
