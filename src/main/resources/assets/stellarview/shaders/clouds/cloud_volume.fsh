#version 150

// Stellar View clouds, volume pass: writes order independent sums.
// RGB is added up (blend ONE, ONE), alpha is multiplied up (blend ZERO, ONE_MINUS_SRC_ALPHA):
//   r = sum(w * shade), g = sum(w), b = sum(w * distance / radius), a = product(1 - alpha)
// The weight w favours near boxes, so the front of a cloud dominates its colour.

in vec3 position;
in float alpha;
in float height;
in float tint;

uniform vec4 Reach;
// xyz: direction the faces are lit from, w: how directional the lighting is
uniform vec4 Light;

out vec4 fragColor;

void main()
{
	// Flat face normal from screen space derivatives; it points toward the camera on front faces
	vec3 normal = cross(dFdx(position), dFdy(position));
	float normalLength = length(normal);
	normal = normalLength > 1e-12 ? normal / normalLength : vec3(0.0, 1.0, 0.0);

	float face = 0.8;
	if(normal.y > 0.7)
		face = 1.0;
	else if(normal.y < -0.7)
		face = 0.62;

	float directional = mix(1.0, 0.78 + 0.32 * max(dot(normal, Light.xyz), 0.0), Light.w);
	float shade = face * mix(0.58, 1.0, height) * tint * directional;

	float distance01 = clamp(length(position) / Reach.x, 0.0, 1.0);
	float nearness = 1.0 - distance01;
	float weight = alpha * (0.02 + nearness * nearness * nearness);

	fragColor = vec4(weight * shade, weight, weight * distance01, alpha);
}
