package com.artlyrics.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the word placement math against Minecraft's camera conventions by projecting text corners the way
 * the game does: camera rotation built like {@code Camera#setRotation}, view = inverse camera rotation,
 * then a perspective projection.
 */
class SkyGeometryTest {
	private static final float DEG = (float) (Math.PI / 180.0);

	/** Screen-space (NDC) position of a text-local point, for a camera looking at (camYaw, camElev). */
	private static Vector3f project(Matrix4f model, float camYaw, float camElev, float textX, float textY) {
		Quaternionf cameraRotation = new Quaternionf().rotationYXZ((float) Math.PI - camYaw * DEG, camElev * DEG, 0f);
		Matrix4f view = new Matrix4f().rotation(new Quaternionf(cameraRotation).conjugate());
		Matrix4f projection = new Matrix4f().perspective(70f * DEG, 16f / 9f, 0.05f, 1000f);
		Vector4f p = new Vector4f(textX, textY, 0f, 1f);
		model.transform(p);
		view.transform(p);
		projection.transform(p);
		return new Vector3f(p.x / p.w, p.y / p.w, p.w);
	}

	@Test
	void directionMatchesMinecraftsFormula() {
		for (float yaw = -180; yaw <= 180; yaw += 37) {
			for (float elev = -60; elev <= 80; elev += 23) {
				Vector3f mine = SkyGeometry.direction(yaw, elev);
				// Vec3.directionFromRotation(xRot, yRot) from Minecraft, with xRot = -elev.
				float xRot = -elev;
				float f = (float) Math.cos(-yaw * DEG - Math.PI);
				float f1 = (float) Math.sin(-yaw * DEG - Math.PI);
				float f2 = (float) -Math.cos(-xRot * DEG);
				float f3 = (float) Math.sin(-xRot * DEG);
				assertEquals(f1 * f2, mine.x, 1e-5);
				assertEquals(f3, mine.y, 1e-5);
				assertEquals(f * f2, mine.z, 1e-5);

				// The facing rotation looks exactly along that direction (camera "forward" is -Z).
				Vector3f forward = SkyGeometry.facing(yaw, elev).transform(new Vector3f(0, 0, -1));
				assertEquals(mine.x, forward.x, 1e-5);
				assertEquals(mine.y, forward.y, 1e-5);
				assertEquals(mine.z, forward.z, 1e-5);
			}
		}
	}

	@Test
	void wordIsCenteredUprightAndReadsLeftToRight() {
		float yaw = 37f;
		float elev = 18f;
		Matrix4f model = SkyGeometry.wordMatrix(new Matrix4f(), yaw, elev, 50f, 0f, 0.2f);

		Vector3f center = project(model, yaw, elev, 0, 0);
		assertEquals(0f, center.x, 1e-4);
		assertEquals(0f, center.y, 1e-4);
		assertTrue(center.z > 0, "in front of the camera");

		Vector3f right = project(model, yaw, elev, 20, 0);
		assertTrue(right.x > 0.01f, "text +x goes to the right of the screen");
		assertEquals(0f, right.y, 1e-4);

		Vector3f down = project(model, yaw, elev, 0, 9);
		assertTrue(down.y < -0.01f, "font +y (next line) goes down the screen");
		assertEquals(0f, down.x, 1e-4);
	}

	@Test
	void anglesMoveWordsTheExpectedWay() {
		float camYaw = -120f;
		float camElev = 15f;
		Vector3f toTheRight = project(SkyGeometry.wordMatrix(new Matrix4f(), camYaw + 10, camElev, 50f, 0f, 0.2f), camYaw, camElev, 0, 0);
		assertTrue(toTheRight.x > 0.1f, "larger yaw appears to the right");
		Vector3f higher = project(SkyGeometry.wordMatrix(new Matrix4f(), camYaw, camElev + 8, 50f, 0f, 0.2f), camYaw, camElev, 0, 0);
		assertTrue(higher.y > 0.1f, "larger elevation appears higher");
	}

	@Test
	void positiveRollTiltsTheRightSideUp() {
		float yaw = 200f;
		float elev = 25f;
		Matrix4f model = SkyGeometry.wordMatrix(new Matrix4f(), yaw, elev, 50f, 0.3f, 0.2f);
		Vector3f right = project(model, yaw, elev, 20, 0);
		assertTrue(right.x > 0 && right.y > 0.005f, "right end of a word on the rising half of the arc is higher");
	}

	@Test
	void wordSizeMatchesItsAngularSize() {
		// 9 font pixels at 0.0042 rad/px should span ~2.17 degrees, whatever the distance.
		for (float distance : new float[] {20f, 50f, 90f}) {
			float radPerPx = 0.0042f;
			Matrix4f model = SkyGeometry.wordMatrix(new Matrix4f(), 0f, 20f, distance, 0f, distance * radPerPx);
			Vector4f top = model.transform(new Vector4f(0, -4.5f, 0, 1));
			Vector4f bottom = model.transform(new Vector4f(0, 4.5f, 0, 1));
			double angle = new Vector3f(top.x, top.y, top.z).angle(new Vector3f(bottom.x, bottom.y, bottom.z));
			assertEquals(9 * radPerPx, angle, 1e-3);
		}
	}
}
