package com.shadowz.enchantedstick;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum StickEffect {
	BLACK_HOLE("black_hole", StickEnchantments.BLACK_HOLE, 100),
	THORNS("thorns", StickEnchantments.THORNS, 0),
	EXPLOSION("explosion", StickEnchantments.EXPLOSION, 60),
	HEAVY("heavy", StickEnchantments.HEAVY, 100),
	INFECTION("infection", StickEnchantments.INFECTION, 100),
	BOUNCE("bounce", StickEnchantments.BOUNCE, 0),
	SHATTER("shatter", StickEnchantments.SHATTER, 0),
	INFINITY("infinity", StickEnchantments.INFINITY, 0),
	SPEED("speed", StickEnchantments.SPEED, 0),
	CHARGE("charge", StickEnchantments.CHARGE, 0);

	private final String id;
	private final RegistryKey<Enchantment> enchantmentKey;
	private final int durationTicks;

	StickEffect(String id, RegistryKey<Enchantment> enchantmentKey, int durationTicks) {
		this.id = id;
		this.enchantmentKey = enchantmentKey;
		this.durationTicks = durationTicks;
	}

	public String id() {
		return this.id;
	}

	public RegistryKey<Enchantment> enchantmentKey() {
		return this.enchantmentKey;
	}

	public int durationTicks() {
		return this.durationTicks;
	}

	public boolean isTimed() {
		return this.durationTicks > 0;
	}

	public static StickEffect byId(String id) {
		return Arrays.stream(values())
			.filter(effect -> effect.id.equals(id.toLowerCase(Locale.ROOT)))
			.findFirst()
			.orElse(null);
	}

	public static List<StickEffect> orderedValues() {
		return List.of(values());
	}
}