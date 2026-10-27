#version 150

// Stellar View clouds, resolve pass: turns the accumulated sums into colour and opacity.
// Colour is an analytic model of daylight, twilight, night, weather and fog; there is no lookup table.

uniform sampler2D Accumulation;
uniform mat4 ViewProj;
uniform mat4 InverseViewProj;
uniform vec3 SunDir;
// x: daylight, y: twilight glow, z: moonlight, w: night vision
uniform vec4 Sky;
// x: rain, y: thunder, z: lightning flash
uniform vec4 Weather;
// x: brightness, y: haze strength, z: cloud radius,
// w: pass. 0: faint pixels, colour only. 1: mostly opaque pixels, colour and depth. 2: every pixel, colour and depth
uniform vec4 Grade;
uniform vec3 FogColor;

in vec2 screen;

out vec4 fragColor;

// When drawing into the main target, pixels at least this opaque write a cloud depth (so rain behind thick clouds is
// hidden) and fainter ones never do. In Fabulous the depth only sorts the clouds layer, so every pixel writes it
const float DEPTH_OPACITY = 0.5;

// Henyey-Greenstein phase function: light scattered forward through thin cloud
float scattering(float cosine, float g)
{
	float g2 = g * g;
	return (1.0 - g2) / pow(1.0 + g2 - 2.0 * g * cosine, 1.5);
}

void main()
{
	vec4 sums = texelFetch(Accumulation, ivec2(gl_FragCoord.xy), 0);
	float opacity = 1.0 - sums.a;
	if(opacity < 0.004 || sums.g <= 0.0)
		discard;

	bool writeDepth = Grade.w > 0.5;
	if(Grade.w < 1.5 && writeDepth != (opacity >= DEPTH_OPACITY))
		discard;

	float shade = sums.r / sums.g;
	float distance01 = clamp(sums.b / sums.g, 0.0, 1.0);

	vec4 farPoint = InverseViewProj * vec4(screen, 1.0, 1.0);
	vec3 direction = normalize(farPoint.xyz / farPoint.w);
	float towardSun = dot(direction, SunDir);

	float day = Sky.x;
	float glow = Sky.y;
	float moon = Sky.z;

	// Base colours of the lit and the shaded side of a cloud
	vec3 lit = mix(vec3(0.15, 0.18, 0.27) * (0.6 + 0.4 * moon), vec3(1.0, 0.985, 0.96), day);
	vec3 dark = mix(vec3(0.055, 0.065, 0.11), vec3(0.6, 0.67, 0.8), day);

	// Sunrise and sunset: warm toward the sun, violet away from it, hot right next to it
	float sunSide = smoothstep(-0.5, 1.0, towardSun);
	vec3 warmLit = mix(vec3(0.62, 0.48, 0.66), vec3(1.0, 0.62, 0.38), sunSide) + vec3(0.3, 0.22, 0.06) * pow(max(towardSun, 0.0), 10.0);
	vec3 warmDark = mix(vec3(0.25, 0.22, 0.36), vec3(0.52, 0.32, 0.3), sunSide);
	lit = mix(lit, warmLit, glow);
	dark = mix(dark, warmDark, glow);

	// Overcast skies flatten the contrast between lit tops and shaded bottoms
	float overcast = clamp(Weather.x * 0.7 + Weather.y * 0.3, 0.0, 1.0);
	dark = mix(dark, lit * 0.75, overcast * 0.45);

	vec3 colour = mix(dark, lit, clamp((shade - 0.25) / 0.8, 0.0, 1.15));

	// Silver lining: thin parts light up around the sun, and faintly around the moon
	float thin = 1.0 - opacity;
	float clearSky = 1.0 - overcast;
	colour += lit * scattering(towardSun, 0.6) * 0.07 * thin * max(day, glow) * clearSky;
	colour += vec3(0.55, 0.62, 0.85) * scattering(-towardSun, 0.6) * 0.025 * thin * (1.0 - day) * moon * clearSky;

	// Rain greys and darkens, thunder much more
	float luminance = dot(colour, vec3(0.299, 0.587, 0.114));
	colour = mix(colour, vec3(luminance), clamp(Weather.x * 0.55 + Weather.y * 0.35, 0.0, 1.0));
	colour *= 1.0 - 0.3 * Weather.x - 0.4 * Weather.y;

	colour = mix(colour, vec3(0.8, 0.8, 1.0), Weather.z);

	// Night vision brightens like it does for the world: the brightest channel goes to full
	float peak = max(max(colour.r, colour.g), max(colour.b, 0.0001));
	colour = mix(colour, colour / peak, Sky.w);

	// Far clouds take on the fog colour, so they melt into the horizon like terrain does
	colour = mix(colour, FogColor, smoothstep(0.25, 1.0, distance01) * Grade.y);
	colour = max(colour * Grade.x, vec3(0.0));

	if(writeDepth)
	{
		vec4 clip = ViewProj * vec4(direction * (distance01 * Grade.z), 1.0);
		gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
	}
	else
	{
		gl_FragDepth = gl_FragCoord.z;
	}

	fragColor = vec4(colour, opacity);
}
