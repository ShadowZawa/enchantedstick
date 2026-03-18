package com.shadowz.enchantedstick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnchantedStick implements ModInitializer {
	public static final String MOD_ID = "enchantedstick";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		StickEffectHandler.register();
		registerCustomEnchantedBooks();

		LOGGER.info("EnchantedStick initialized.");
	}

	private static void registerCustomEnchantedBooks() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			RegistryWrapper.Impl<Enchantment> enchantmentRegistry = entries.getContext().lookup().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);

			for (RegistryKey<Enchantment> key : StickEnchantments.ALL) {
				enchantmentRegistry.getOptional(key).ifPresent(entry -> {
					ItemStack book = EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(entry, 1));
					entries.add(book);
				});
			}
		});
	}
}