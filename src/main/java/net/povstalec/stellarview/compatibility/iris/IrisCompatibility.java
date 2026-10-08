package net.povstalec.stellarview.compatibility.iris;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.fabricmc.loader.api.FabricLoader;
import net.povstalec.stellarview.StellarView;
import net.povstalec.stellarview.common.config.GeneralConfig;

/**
 * Talks to the Iris API without Iris being a compile time dependency.
 * Only meant to be used on the client.
 */
public final class IrisCompatibility
{
	private static final MethodHandle IS_SHADER_PACK_IN_USE;

	private static boolean failureLogged = false;
	private static boolean skyYielded = false;

	static
	{
		MethodHandle handle = null;

		if(FabricLoader.getInstance().isModLoaded(StellarView.IRIS_MODID))
		{
			try
			{
				Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				Object api = apiClass.getMethod("getInstance").invoke(null);

				handle = MethodHandles.publicLookup()
						.findVirtual(apiClass, "isShaderPackInUse", MethodType.methodType(boolean.class))
						.bindTo(api)
						.asType(MethodType.methodType(boolean.class));

				StellarView.LOGGER.info("Iris API found, Stellar View will hand the sky over to active shader packs");
			}
			catch(Throwable e)
			{
				handle = null;
				StellarView.LOGGER.warn("Could not reach the Iris API, the shader hand-over is unavailable", e);
			}
		}

		IS_SHADER_PACK_IN_USE = handle;
	}

	private IrisCompatibility() {}

	public static boolean isShaderPackInUse()
	{
		if(IS_SHADER_PACK_IN_USE == null)
			return false;

		try
		{
			return (boolean) IS_SHADER_PACK_IN_USE.invokeExact();
		}
		catch(Throwable e)
		{
			if(!failureLogged)
			{
				failureLogged = true;
				StellarView.LOGGER.warn("Failed to query the Iris API, assuming no shader pack is in use", e);
			}

			return false;
		}
	}

	/**
	 * Whether Stellar View should leave the sky to Vanilla (and through it to the shader pack) right now.
	 * Called every frame, so it only logs when the answer changes.
	 */
	public static boolean shouldYieldSky()
	{
		boolean yieldSky = GeneralConfig.disable_with_shaders.get() && isShaderPackInUse();

		if(yieldSky != skyYielded)
		{
			skyYielded = yieldSky;

			if(yieldSky)
				StellarView.LOGGER.info("Shader pack active: Stellar View sky rendering paused");
			else
				StellarView.LOGGER.info("Shader pack inactive: Stellar View sky rendering resumed");
		}

		return yieldSky;
	}
}
