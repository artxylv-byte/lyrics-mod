package com.artlyrics.compat.mixin;

import com.artlyrics.compat.Mc;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.21.9: the main world pass draws the solid terrain first, right after the sky, so that is where the
 * animated sky goes (Fabric API has no event for it in this version).
 */
@Mixin(ChunkSectionsToRender.class)
public abstract class ChunkSectionsMixin {
	@Inject(method = "renderGroup", at = @At("HEAD"))
	private void artlyrics$beforeTerrain(ChunkSectionLayerGroup group, CallbackInfo ci) {
		if (group == ChunkSectionLayerGroup.OPAQUE) {
			Mc.fireSkyStage();
		}
	}
}
