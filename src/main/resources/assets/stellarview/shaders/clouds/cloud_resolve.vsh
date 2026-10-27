#version 150

// Stellar View clouds, resolve pass: one triangle that covers the whole screen, no vertex data needed.

out vec2 screen;

void main()
{
	vec2 corner = vec2(gl_VertexID == 1 ? 3.0 : -1.0, gl_VertexID == 2 ? 3.0 : -1.0);
	screen = corner;
	gl_Position = vec4(corner, 0.0, 1.0);
}
