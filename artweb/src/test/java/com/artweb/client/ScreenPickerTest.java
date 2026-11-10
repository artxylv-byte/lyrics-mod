package com.artweb.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenPickerTest {
	private static final BlockPos POS = new BlockPos(2, 101, 4);
	private static final Vec3 EYE = new Vec3(0.5, 102.62, 0.5);

	@Test
	void hitsThePictureInFront() {
		// Screen faces north, the player stands north of it looking south: the 4 × 3 picture reaches west and up.
		ScreenPicker.Placement p = new ScreenPicker.Placement(Direction.NORTH, 4, 3, 0);
		double t = ScreenPicker.hit(POS, p, EYE, new Vec3(0, 0, 1));
		assertEquals(4.5 - ScreenPicker.FACE_OFFSET - 0.5, t, 1e-6);
	}

	@Test
	void missesBesideAboveAndBehind() {
		ScreenPicker.Placement p = new ScreenPicker.Placement(Direction.NORTH, 4, 3, 0);
		// East of the block is the picture's left edge: looking there misses.
		assertEquals(-1, ScreenPicker.hit(POS, p, new Vec3(3.5, 102.62, 0.5), new Vec3(0, 0, 1)));
		// Above the picture.
		assertEquals(-1, ScreenPicker.hit(POS, p, new Vec3(0.5, 104.5, 0.5), new Vec3(0, 0, 1)));
		// From behind.
		assertEquals(-1, ScreenPicker.hit(POS, p, new Vec3(0.5, 102.62, 8.5), new Vec3(0, 0, -1)));
		// A 1 × 1 screen only covers its own block.
		ScreenPicker.Placement small = new ScreenPicker.Placement(Direction.NORTH, 1, 1, 0);
		assertEquals(-1, ScreenPicker.hit(POS, small, EYE, new Vec3(0, 0, 1)));
	}

	@Test
	void worksForEveryDirection() {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			// Stand 3 blocks in front of the block, look back at its centre.
			Vec3 front = Vec3.atCenterOf(POS).add(Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(3));
			Vec3 dir = Vec3.atCenterOf(POS).subtract(front).normalize();
			ScreenPicker.Placement p = new ScreenPicker.Placement(facing, 1, 1, 0);
			assertEquals(3 - ScreenPicker.FACE_OFFSET, ScreenPicker.hit(POS, p, front, dir), 1e-6, facing.toString());
		}
	}
}
