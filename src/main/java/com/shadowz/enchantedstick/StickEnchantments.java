package com.shadowz.enchantedstick;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.List;

public final class StickEnchantments {
	public static final RegistryKey<Enchantment> BLACK_HOLE = key("black_hole");
	public static final RegistryKey<Enchantment> THORNS = key("thorns");
	public static final RegistryKey<Enchantment> EXPLOSION = key("explosion");
	public static final RegistryKey<Enchantment> HEAVY = key("heavy");
	public static final RegistryKey<Enchantment> INFECTION = key("infection");
	public static final RegistryKey<Enchantment> BOUNCE = key("bounce");
	public static final RegistryKey<Enchantment> SHATTER = key("shatter");
	public static final RegistryKey<Enchantment> INFINITY = key("infinity");
	public static final RegistryKey<Enchantment> CHARGE = key("charge");

	public static final List<RegistryKey<Enchantment>> ALL = List.of(
		BLACK_HOLE,
		THORNS,
		EXPLOSION,
		HEAVY,
		INFECTION,
		BOUNCE,
		SHATTER,
		INFINITY,
		CHARGE
	);

	private StickEnchantments() {
	}

	private static RegistryKey<Enchantment> key(String path) {
		return RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(EnchantedStick.MOD_ID, path));
	}
}