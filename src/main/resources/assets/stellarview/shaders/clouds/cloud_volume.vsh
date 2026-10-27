#version 150

// Stellar View clouds, volume pass: expands one unit box per instance into a cloud puff.
// The box is only the hull the puff is rasterised with; cloud_volume.fsh traces the view ray through it.

// Unit box corner in [0, 1]
in vec3 Corner;
// Per box: x, z relative to the window origin, density, random value in [0, 1)
in vec4 Box;

uniform mat4 ViewProj;
// Window origin relative to the camera, y is the cloud base
uniform vec3 Origin;
// x: box half width, y: height of the thinnest boxes, z: extra height of the thickest cores, w: 1 for flat sheets
uniform vec4 Shape;
// x: density threshold, y: grow band, z: edge fuzz, w: opacity of one box
uniform vec4 Cover;
// x: cloud radius, y: fade start, z: fade end, w: box scale at the radius
uniform vec4 Reach;
// Seconds, wraps every 1200
uniform float Time;

out vec3 position;
flat out float alpha;
flat out float tint;
// The box relative to the camera: centre and half size
flat out vec3 boxCentre;
flat out vec3 boxHalf;
// Height of the box's bottom and top within the thickest possible box, in [0, 1]
flat out vec2 heightRange;

// The camera counts as inside a box this close to it, so the near plane never cuts a hole into its front faces
const float INSIDE_MARGIN = 0.5;

void main()
{
	float random = Box.w;
	float key = Box.z - Cover.z * random;

	// Weather and coverage move the threshold: boxes near it grow in and fade in smoothly instead of popping
	float grow = smoothstep(Cover.x, Cover.x + Cover.y, key);
	float strength = clamp((key - Cover.x) / 0.35, 0.0, 1.0);

	float tallest = Shape.y + Shape.z;
	float top = (Shape.y + Shape.z * pow(strength, 0.8)) * (0.55 + 0.45 * grow);
	float bottom = (1.0 - strength) * 1.5 + fract(random * 13.7 + 0.21) * 2.0;
	top = max(top, bottom + 1.0);
	if(Shape.w > 0.5)
	{
		top = 0.0;
		bottom = 0.0;
	}

	vec2 centre = Box.xy + Origin.xz;
	float centreDistance = length(vec3(centre.x, Origin.y + (top + bottom) * 0.5, centre.y));
	float edge = clamp(centreDistance / Reach.x, 0.0, 1.0);
	float shrink = mix(1.0, Reach.w, edge * edge);

	float breathing = 1.0 + 0.05 * sin(Time * 0.3141593 + random * 6.283185);
	float halfWidth = Shape.x * shrink * breathing * (0.55 + 0.45 * grow) * (0.85 + 0.3 * fract(random * 7.13 + 0.37));

	float nearFade = smoothstep(Shape.x * 0.6, Shape.x * 2.4, centreDistance);
	float farFade = 1.0 - smoothstep(Reach.y, Reach.z, centreDistance);
	alpha = Cover.w * grow * nearFade * farFade;
	tint = 0.9 + 0.2 * fract(random * 3.71 + 0.11);

	if(alpha < 0.002)
	{
		// Invisible box: every vertex lands on the same point outside the clip volume, nothing is rasterised
		position = vec3(0.0);
		boxCentre = vec3(0.0);
		boxHalf = vec3(1.0);
		heightRange = vec2(1.0);
		gl_Position = vec4(0.0, 0.0, 2.0, 1.0);
		return;
	}

	heightRange = tallest > 0.0 ? clamp(vec2(bottom, top) / tallest, 0.0, 1.0) : vec2(1.0);
	boxCentre = vec3(centre.x, Origin.y + (top + bottom) * 0.5 * shrink, centre.y);
	boxHalf = vec3(halfWidth, (top - bottom) * 0.5 * shrink, halfWidth);

	// Only front faces are drawn (back face culling), so every pixel of the box is shaded exactly once. With the
	// camera inside the box there are no front faces: mirroring the box turns its winding inside out, then the far
	// faces are the ones drawn and the fragment shader starts the ray at the camera
	vec3 corner = Corner;
	if(all(lessThan(abs(boxCentre), boxHalf + INSIDE_MARGIN)))
		corner.x = 1.0 - corner.x;

	float y = mix(bottom, top, corner.y);
	position = vec3(centre.x + (corner.x * 2.0 - 1.0) * halfWidth, Origin.y + y * shrink, centre.y + (corner.z * 2.0 - 1.0) * halfWidth);
	gl_Position = ViewProj * vec4(position, 1.0);
}
