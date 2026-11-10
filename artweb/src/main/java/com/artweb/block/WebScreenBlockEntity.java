package com.artweb.block;

import com.artweb.ArtWeb;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** What a screen shows: the page address, its size in blocks and how far the page is scrolled. */
public class WebScreenBlockEntity extends BlockEntity {
	public static final String DEFAULT_URL = "https://en.wikipedia.org/wiki/Minecraft";
	public static final int MAX_WIDTH = 8;
	public static final int MAX_HEIGHT = 6;
	public static final int MAX_URL_LENGTH = 2048;

	private String url = DEFAULT_URL;
	private int width = 3;
	private int height = 2;
	private int scroll;

	public WebScreenBlockEntity(BlockPos pos, BlockState state) {
		super(ArtWeb.WEB_SCREEN_ENTITY, pos, state);
	}

	public String url() {
		return url;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	/** Page pixels scrolled from the top. */
	public int scroll() {
		return scroll;
	}

	/** Changes what the screen shows and sends it to everyone nearby (server side). */
	public void update(String url, int width, int height, int scroll) {
		this.url = sanitizeUrl(url);
		this.width = Math.max(1, Math.min(MAX_WIDTH, width));
		this.height = Math.max(1, Math.min(MAX_HEIGHT, height));
		this.scroll = Math.max(0, Math.min(1_000_000, scroll));
		setChanged();
		if (level != null) {
			BlockState state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
		}
	}

	/** Only web addresses (http/https) of a sensible length are kept; anything else shows the default page. */
	static String sanitizeUrl(String url) {
		if (url == null || url.isBlank()) {
			return DEFAULT_URL;
		}
		String u = url.strip();
		String lower = u.toLowerCase(java.util.Locale.ROOT);
		if (u.length() > MAX_URL_LENGTH || !(lower.startsWith("http://") || lower.startsWith("https://"))) {
			return DEFAULT_URL;
		}
		return u;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		url = sanitizeUrl(input.getStringOr("url", DEFAULT_URL));
		width = Math.max(1, Math.min(MAX_WIDTH, input.getIntOr("width", 3)));
		height = Math.max(1, Math.min(MAX_HEIGHT, input.getIntOr("height", 2)));
		scroll = Math.max(0, input.getIntOr("scroll", 0));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("url", url);
		output.putInt("width", width);
		output.putInt("height", height);
		output.putInt("scroll", scroll);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}
}
