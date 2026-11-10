package com.artweb.net;

import com.artweb.ArtWeb;
import com.artweb.block.WebScreenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Sent by the browser window when a player changes a screen's page, size or scroll position. */
public record UpdateScreenPayload(BlockPos pos, String url, int width, int height, int scroll) implements CustomPacketPayload {
	public static final Type<UpdateScreenPayload> TYPE = new Type<>(ArtWeb.id("update_screen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UpdateScreenPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, UpdateScreenPayload::pos,
			ByteBufCodecs.stringUtf8(WebScreenBlockEntity.MAX_URL_LENGTH), UpdateScreenPayload::url,
			ByteBufCodecs.VAR_INT, UpdateScreenPayload::width,
			ByteBufCodecs.VAR_INT, UpdateScreenPayload::height,
			ByteBufCodecs.VAR_INT, UpdateScreenPayload::scroll,
			UpdateScreenPayload::new);

	/** How far away a player may be to change a screen. */
	private static final double MAX_DISTANCE = 32;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Applies the change on the server, if the player may build and is close enough to the screen. */
	public void apply(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!player.mayBuild() || !level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE * MAX_DISTANCE) {
			return;
		}
		if (level.getBlockEntity(pos) instanceof WebScreenBlockEntity screen) {
			screen.update(url, width, height, scroll);
		}
	}
}
