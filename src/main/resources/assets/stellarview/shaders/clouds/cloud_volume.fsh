#version 150

// Stellar View clouds, volume pass: writes order independent sums.
// RGB is added up (blend ONE, ONE), alpha is multiplied up (blend ZERO, ONE_MINUS_SRC_ALPHA):
//   r = sum(w * shade), g = sum(w), b = sum(w * distance / radius), a = product(1 - alpha)
// The weight w favours near boxes, so the front of a cloud dominates its colour.
//
// Each box holds a soft puff: an ellipsoid inscribed in the box whose density falls smoothly to zero at its rim.
// The view ray is intersected with the box, the density is taken at the middle of the chord and the opacity grows
// with the chord length. Both reach zero at the box's outline, so no box edge is ever visible, and since every
// box only multiplies the transmittance, overlapping puffs melt into one mass.

in vec3 position;
flat in float alpha;
flat in float tint;
flat in vec3 boxCentre;
flat in vec3 boxHalf;
flat in vec2 heightRange;

// w: 1 for flat sheets
uniform vec4 Shape;
uniform vec4 Reach;
// xyz: direction the faces are lit from, w: how directional the lighting is
uniform vec4 Light;

out vec4 fragColor;

// Fraction of the puff radius that is fully dense; the density falls to zero from here to the rim
const float CORE = 0.3;
// Optical depth of a puff relative to a hard box of the same opacity. The puff averages about a third of the box's
// density over its footprint, so this keeps the overall coverage where the hard boxes had it
const float DENSITY_GAIN = 3.0;
// How quickly the opacity saturates with the chord, in units of the box's thinnest side. Straight through the
// thinnest side counts as 1, the longest chords count as at most 1.16
const float CHORD_RISE = 2.0;

void main()
{
	bool flatSheet = Shape.w > 0.5;

	vec3 entry = position;
	vec3 middle = position;
	float chordFactor = 1.0;
	if(!flatSheet)
	{
		// Slab test of the ray from the camera (the origin) through this fragment against the box, in units of the
		// fragment's distance. Zero direction components are nudged so no division by zero happens
		vec3 ray = position;
		vec3 reciprocal = 1.0 / ((step(0.0, ray) * 2.0 - 1.0) * max(abs(ray), vec3(1e-6)));
		vec3 toMin = (boxCentre - boxHalf) * reciprocal;
		vec3 toMax = (boxCentre + boxHalf) * reciprocal;
		vec3 low = min(toMin, toMax);
		vec3 high = max(toMin, toMax);
		// The camera may be inside the box: the cloud then starts at the camera
		float enter = max(max(max(low.x, low.y), low.z), 0.0);
		float leave = max(min(min(high.x, high.y), high.z), enter);

		entry = ray * enter;
		middle = ray * ((enter + leave) * 0.5);

		float chord = (leave - enter) * length(ray);
		float thinnest = 2.0 * min(boxHalf.x, boxHalf.y);
		chordFactor = (1.0 - exp(-CHORD_RISE * chord / thinnest)) / (1.0 - exp(-CHORD_RISE));
	}

	// Puff density at the middle of the chord. On the box's surface at least one axis is at 1, so the density is 0
	vec3 local = (middle - boxCentre) / max(boxHalf, vec3(1e-4));
	if(flatSheet)
		local.y = 0.0;
	float density = 1.0 - smoothstep(CORE, 1.0, length(local));

	// Beer-Lambert: the box's opacity setting becomes an optical depth, scaled by the puff's density and chord
	float depth = -log(1.0 - min(alpha, 0.999)) * DENSITY_GAIN * density * chordFactor;
	float opacity = 1.0 - exp(-depth);

	// Shading from where the ray enters the puff: top brighter, sides in between, bottom darker.
	// The normal is the puff's own (an ellipsoid's gradient), so it turns smoothly around the box's edges
	vec3 surface;
	vec3 normal;
	if(flatSheet)
	{
		surface = vec3(0.0, position.y < 0.0 ? 1.0 : -1.0, 0.0);
		normal = surface;
	}
	else
	{
		surface = (entry - boxCentre) / boxHalf;
		normal = normalize(surface / boxHalf + vec3(0.0, 1e-6, 0.0));
	}

	float up = clamp(surface.y * 0.5 + 0.5, 0.0, 1.0);
	float face = mix(0.62, 1.0, up);
	float height = mix(heightRange.x, heightRange.y, up);

	float directional = mix(1.0, 0.78 + 0.32 * max(dot(normal, Light.xyz), 0.0), Light.w);
	float shade = face * mix(0.58, 1.0, height) * tint * directional;

	float distance01 = clamp(length(middle) / Reach.x, 0.0, 1.0);
	float nearness = 1.0 - distance01;
	float weight = opacity * (0.02 + nearness * nearness * nearness);

	fragColor = vec4(weight * shade, weight, weight * distance01, opacity);
}
