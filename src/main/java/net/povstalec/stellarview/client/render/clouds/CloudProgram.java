package net.povstalec.stellarview.client.render.clouds;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.povstalec.stellarview.StellarView;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A GLSL program built from two source files under {@code assets/stellarview/shaders/clouds/}.
 * <p>
 * Deliberately not a Vanilla {@code ShaderInstance}: a compile error in a {@code ShaderInstance} registered
 * through Vanilla's shader reload fails the whole resource reload, while this one only disables the clouds.
 * Render thread only.
 */
final class CloudProgram
{
	private int id;

	private CloudProgram(int id)
	{
		this.id = id;
	}

	/**
	 * Compiles and links a program.
	 * @param name				Base file name, the files are {@code name.vsh} and {@code name.fsh}
	 * @param attributeNames	Vertex attributes, bound to locations 0, 1, ... in this order
	 * @throws CloudSetupException with the driver's log if anything fails
	 */
	static CloudProgram create(String name, String... attributeNames) throws CloudSetupException
	{
		String vertexSource = load(name + ".vsh");
		String fragmentSource = load(name + ".fsh");

		int vertex = 0;
		int fragment = 0;
		int program = 0;
		try
		{
			vertex = compile(GL20.GL_VERTEX_SHADER, vertexSource, name + ".vsh");
			fragment = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource, name + ".fsh");

			program = GL20.glCreateProgram();
			if(program == 0)
				throw new CloudSetupException("Could not create the program " + name);

			GL20.glAttachShader(program, vertex);
			GL20.glAttachShader(program, fragment);
			for(int i = 0; i < attributeNames.length; i++)
			{
				GL20.glBindAttribLocation(program, i, attributeNames[i]);
			}
			GL30.glBindFragDataLocation(program, 0, "fragColor");
			GL20.glLinkProgram(program);

			if(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
				throw new CloudSetupException("Could not link " + name + ": " + GL20.glGetProgramInfoLog(program, 32768).trim());

			GL20.glDetachShader(program, vertex);
			GL20.glDetachShader(program, fragment);

			CloudProgram result = new CloudProgram(program);
			program = 0; // Owned by the result now
			return result;
		}
		finally
		{
			if(vertex != 0)
				GL20.glDeleteShader(vertex);
			if(fragment != 0)
				GL20.glDeleteShader(fragment);
			if(program != 0)
				GL20.glDeleteProgram(program);
		}
	}

	int id()
	{
		return id;
	}

	/**
	 * @return The location of a uniform, or -1 if the driver optimised it away (setting -1 is a no-op in OpenGL)
	 */
	int uniform(String name)
	{
		return GL20.glGetUniformLocation(id, name);
	}

	void delete()
	{
		if(id != 0)
		{
			GL20.glDeleteProgram(id);
			id = 0;
		}
	}

	private static int compile(int type, String source, String fileName) throws CloudSetupException
	{
		int shader = GL20.glCreateShader(type);
		if(shader == 0)
			throw new CloudSetupException("Could not create a shader for " + fileName);

		GL20.glShaderSource(shader, source);
		GL20.glCompileShader(shader);

		if(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
		{
			String log = GL20.glGetShaderInfoLog(shader, 32768).trim();
			GL20.glDeleteShader(shader);
			throw new CloudSetupException("Could not compile " + fileName + ": " + log);
		}

		return shader;
	}

	private static String load(String fileName) throws CloudSetupException
	{
		ResourceLocation location = ResourceLocation.fromNamespaceAndPath(StellarView.MODID, "shaders/clouds/" + fileName);
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
		if(resource.isEmpty())
			throw new CloudSetupException("Missing shader file " + location);

		try(BufferedReader reader = resource.get().openAsReader())
		{
			return reader.lines().collect(Collectors.joining("\n", "", "\n"));
		}
		catch(IOException e)
		{
			throw new CloudSetupException("Could not read " + location + ": " + e.getMessage());
		}
	}
}
