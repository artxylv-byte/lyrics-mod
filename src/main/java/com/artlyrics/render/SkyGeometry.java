package com.artlyrics.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The math for placing one word in 3D, kept free of Minecraft classes so it can be unit-tested.
 *
 * <p>Angles use Minecraft's conventions: {@code yaw} is {@code Entity#getYRot} (0 = south/+Z, increasing
 * when turning right) and {@code elev} is degrees above the horizon (the negative of {@code xRot}).
 * All positions are relative to the camera.
 */
final class SkyGeometry {
	private static final float DEG = (float) (Math.PI / 180.0);

	private SkyGeometry() {
	}

	/** Unit vector from the camera towards (yaw, elev); same as {@code Vec3.directionFromRotation(-elev, yaw)}. */
	static Vector3f direction(float yawDeg, float elevDeg) {
		float yaw = yawDeg * DEG;
		float elev = elevDeg * DEG;
		float cosElev = (float) Math.cos(elev);
		return new Vector3f((float) -Math.sin(yaw) * cosElev, (float) Math.sin(elev), (float) Math.cos(yaw) * cosElev);
	}

	/**
	 * The orientation a camera would have when looking straight at (yaw, elev), built exactly like
	 * {@code Camera#setRotation}. Applying it to text (as vanilla does for name tags) makes the text face
	 * the camera.
	 */
	static Quaternionf facing(float yawDeg, float elevDeg) {
		return new Quaternionf().rotationYXZ((float) Math.PI - yawDeg * DEG, elevDeg * DEG, 0f);
	}

	/**
	 * Model matrix for a word: moved {@code distance} blocks towards (yaw, elev), turned to face the camera,
	 * tilted by {@code rollRad}, and scaled so one font pixel is {@code blocksPerPixel} blocks
	 * (with Y flipped, because font coordinates grow downwards).
	 */
	static Matrix4f wordMatrix(Matrix4f base, float yawDeg, float elevDeg, float distance, float rollRad, float blocksPerPixel) {
		Vector3f dir = direction(yawDeg, elevDeg).mul(distance);
		Matrix4f m = new Matrix4f(base)
				.translate(dir)
				.rotate(facing(yawDeg, elevDeg));
		if (rollRad != 0f) {
			m.rotateZ(rollRad);
		}
		return m.scale(blocksPerPixel, -blocksPerPixel, blocksPerPixel);
	}
}
