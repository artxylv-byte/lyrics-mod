package com.artweb;

import com.artweb.block.WebScreenBlock;
import com.artweb.block.WebScreenBlockEntity;
import com.artweb.net.UpdateScreenPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ArtWeb: Web Screen blocks that show websites. Registers the block, its item and the network packet. */
public final class ArtWeb implements ModInitializer {
	public static final String MOD_ID = "artweb";
	public static final Logger LOGGER = LoggerFactory.getLogger("ArtWeb");

	public static Block WEB_SCREEN;
	public static Item WEB_SCREEN_ITEM;
	public static BlockEntityType<WebScreenBlockEntity> WEB_SCREEN_ENTITY;

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("web_screen"));
		WEB_SCREEN = Registry.register(BuiltInRegistries.BLOCK, blockKey, new WebScreenBlock(BlockBehaviour.Properties.of()
				.setId(blockKey)
				.mapColor(MapColor.COLOR_BLACK)
				.strength(1.5f, 6f)
				.sound(SoundType.METAL)
				.lightLevel(state -> 6)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id("web_screen"));
		WEB_SCREEN_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(WEB_SCREEN, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

		WEB_SCREEN_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("web_screen"),
				FabricBlockEntityTypeBuilder.create(WebScreenBlockEntity::new, WEB_SCREEN).build());

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(WEB_SCREEN_ITEM));

		PayloadTypeRegistry.playC2S().register(UpdateScreenPayload.TYPE, UpdateScreenPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(UpdateScreenPayload.TYPE, (payload, context) -> payload.apply(context.player()));

		LOGGER.info("[ArtWeb] Ready.");
	}
}
