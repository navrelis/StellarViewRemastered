package net.povstalec.stellarview.client.render.clouds;

/**
 * Thrown when the cloud renderer's GPU resources cannot be created (shader error, incomplete framebuffer, GL error).
 * The renderer logs it once and falls back to Vanilla clouds for the rest of the session.
 */
final class CloudSetupException extends Exception
{
	private static final long serialVersionUID = 1L;

	CloudSetupException(String message)
	{
		super(message);
	}
}
