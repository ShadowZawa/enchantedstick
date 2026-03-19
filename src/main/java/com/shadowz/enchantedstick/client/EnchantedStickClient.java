package com.shadowz.enchantedstick.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.item.ModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class EnchantedStickClient implements net.fabricmc.api.ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register a custom predicate for enchanted books with custom model data
		ModelPredicateProviderRegistry.register(
			Items.ENCHANTED_BOOK,
			Identifier.of("enchantedstick:custom_book"),
			(stack, world, entity, seed) -> {
				// Return 1.0 if this is our custom enchanted book
				CustomModelDataComponent cmd = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
				return (cmd != null && cmd.value() == 1) ? 1.0f : 0.0f;
			}
		);
	}
}
