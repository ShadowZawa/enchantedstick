package com.shadowz.enchantedstick;

import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;

import java.util.Optional;

public final class LibrarianTradeRegistrar {
	private static final int BOOK_PRICE = 10;
	private static final int MAX_USES = 12;
	private static final int VILLAGER_XP = 1;
	private static final int LIBRARIAN_LEVEL = 1;
	private static final float PRICE_MULTIPLIER = 0.0f;

	private LibrarianTradeRegistrar() {
	}

	public static void register() {
		TradeOfferHelper.registerVillagerOffers(VillagerProfession.LIBRARIAN, LIBRARIAN_LEVEL, factories -> {
			for (RegistryKey<Enchantment> enchantmentKey : StickEnchantments.ALL) {
				factories.add((entity, random) -> createOffer(entity.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT), enchantmentKey));
			}
		});
	}

	private static TradeOffer createOffer(Registry<Enchantment> enchantmentRegistry, RegistryKey<Enchantment> enchantmentKey) {
		Optional<RegistryEntry.Reference<Enchantment>> enchantment = enchantmentRegistry.getEntry(enchantmentKey);

		if (enchantment.isEmpty()) {
			return null;
		}

		ItemStack book = EnchantedStick.createCustomEnchantedBook(enchantment.get());
		return new TradeOffer(new TradedItem(Items.EMERALD, BOOK_PRICE), book, MAX_USES, VILLAGER_XP, PRICE_MULTIPLIER);
	}
}