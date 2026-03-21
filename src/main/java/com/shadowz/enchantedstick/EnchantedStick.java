package com.shadowz.enchantedstick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnchantedStick implements ModInitializer {
	public static final String MOD_ID = "enchantedstick";
	public static final ItemGroup ENCHANTED_STICK_GROUP = Registry.register(
		Registries.ITEM_GROUP,
		Identifier.of(MOD_ID, "main"),
		FabricItemGroup.builder()
			.displayName(Text.translatable("itemGroup.enchantedstick.main"))
			.icon(() -> new ItemStack(Items.STICK))
			.entries((displayContext, entries) -> addCustomEnchantedBooks(displayContext.lookup().getWrapperOrThrow(RegistryKeys.ENCHANTMENT), entries))
			.build()
	);

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		StickEffectHandler.register();
		LibrarianTradeRegistrar.register();

		LOGGER.info("EnchantedStick initialized.");
	}

	private static void addCustomEnchantedBooks(RegistryWrapper.Impl<Enchantment> enchantmentRegistry, ItemGroup.Entries entries) {
		for (RegistryKey<Enchantment> key : StickEnchantments.ALL) {
			enchantmentRegistry.getOptional(key).ifPresent(entry -> {
				entries.add(createCustomEnchantedBook(entry));
			});
		}
	}

	public static ItemStack createCustomEnchantedBook(RegistryEntry<Enchantment> enchantment) {
		ItemStack book = EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(enchantment, 1));
		book.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(1));
		return book;
	}
}