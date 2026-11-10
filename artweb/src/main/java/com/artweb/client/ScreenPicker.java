package com.artweb.client;

import com.artweb.block.WebScreenBlockEntity;
import com.artweb.client.gui.BrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets players right-click anywhere on a screen's picture, not only on the block it comes from. The renderer
 * reports where each visible screen is; each tick, if the crosshair is on one of them and nothing is in front of
 * it, a right-click opens that screen's browser window instead of doing anything else. Sneaking skips this, so
 * blocks can still be placed as usual.
 */
public final class ScreenPicker {
	/** How far away a picture can be clicked, in blocks. */
	private static final double REACH = 8;
	/** The picture floats this far in front of the block's face (matches the renderer). */
	public static final float FACE_OFFSET = 0.504f;

	record Placement(Direction facing, int width, int height, long seenAt) {
	}

	private static final Map<BlockPos, Placement> VISIBLE = new ConcurrentHashMap<>();

	private ScreenPicker() {
	}

	/** Called by the renderer for every screen it draws. */
	public static void seen(BlockPos pos, Direction facing, int width, int height) {
		VISIBLE.put(pos.immutable(), new Placement(facing, width, height, System.currentTimeMillis()));
	}

	/** Start of each client tick, before Minecraft handles the use key. */
	public static void tick(Minecraft mc) {
		if (mc.screen != null || mc.player == null || mc.level == null || mc.player.isShiftKeyDown() || mc.player.isSpectator()) {
			return;
		}
		BlockPos target = pick(mc);
		if (target == null) {
			return;
		}
		boolean clicked = false;
		while (mc.options.keyUse.consumeClick()) {
			clicked = true;
		}
		if (clicked && mc.level.getBlockEntity(target) instanceof WebScreenBlockEntity screen) {
			mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			mc.setScreen(new BrowserScreen(target, screen));
		}
	}

	/** The screen whose picture is under the crosshair, or null. */
	private static BlockPos pick(Minecraft mc) {
		long now = System.currentTimeMillis();
		Vec3 eye = mc.player.getEyePosition(1f);
		Vec3 dir = mc.player.getViewVector(1f);
		BlockPos best = null;
		double bestT = REACH;
		for (Iterator<Map.Entry<BlockPos, Placement>> it = VISIBLE.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<BlockPos, Placement> e = it.next();
			Placement p = e.getValue();
			if (now - p.seenAt > 2000) {
				it.remove();
				continue;
			}
			double t = hit(e.getKey(), p, eye, dir);
			if (t >= 0 && t < bestT) {
				bestT = t;
				best = e.getKey();
			}
		}
		if (best == null) {
			return null;
		}
		// Something closer than the picture (a block, a mob) gets the click as usual.
		HitResult vanilla = mc.hitResult;
		if (vanilla != null && vanilla.getType() != HitResult.Type.MISS) {
			boolean isThisScreen = vanilla instanceof BlockHitResult b && b.getBlockPos().equals(best);
			if (!isThisScreen && vanilla.getLocation().distanceTo(eye) < bestT - 0.01) {
				return null;
			}
		}
		return best;
	}

	/** Distance along the view ray to the picture of the screen at {@code pos}, or -1 if the ray misses it. */
	static double hit(BlockPos pos, Placement p, Vec3 eye, Vec3 dir) {
		Vec3 n = Vec3.atLowerCornerOf(p.facing.getUnitVec3i());
		Vec3 right = Vec3.atLowerCornerOf(p.facing.getCounterClockWise().getUnitVec3i());
		Vec3 up = new Vec3(0, 1, 0);
		double facing = dir.dot(n);
		if (facing >= -1e-4) {
			return -1; // looking at it from behind or edge-on
		}
		Vec3 corner = Vec3.atCenterOf(pos).add(n.scale(FACE_OFFSET)).subtract(right.scale(0.5)).subtract(up.scale(0.5));
		double t = corner.subtract(eye).dot(n) / facing;
		if (t < 0) {
			return -1;
		}
		Vec3 onPlane = eye.add(dir.scale(t)).subtract(corner);
		double u = onPlane.dot(right);
		double v = onPlane.dot(up);
		return u >= 0 && u <= p.width && v >= 0 && v <= p.height ? t : -1;
	}
}
