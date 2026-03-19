package com.shadowz.enchantedstick;

import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

public final class InfinityItemSupport {
	private static final String INFINITE_KEY = "enchantedstick_infinite";
	private static final String BLOCK_ID_KEY = "enchantedstick_block_id";

	private InfinityItemSupport() {
	}

	public static ItemStack createInfiniteBlockStack(Block block) {
		ItemStack stack = new ItemStack(block.asItem());
		NbtCompound data = getCustomData(stack);
		data.putBoolean(INFINITE_KEY, true);
		data.putString(BLOCK_ID_KEY, Registries.BLOCK.getId(block).toString());
		setCustomData(stack, data);
		stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(stack.getName().getString() + " [Infinity]"));
		return stack;
	}

	public static boolean isInfiniteBlock(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		return getCustomData(stack).getBoolean(INFINITE_KEY);
	}

	public static ItemStack createNormalDrop(ItemStack stack) {
		ItemStack normal = new ItemStack(stack.getItem(), stack.getCount());
		return normal;
	}

	public static ItemStack createRestoredStack(ItemStack stack) {
		ItemStack restored = stack.copy();
		restored.setCount(stack.getCount());
		return restored;
	}

	public static void stripInfinityData(ItemStack stack) {
		NbtCompound data = getCustomData(stack);
		data.remove(INFINITE_KEY);
		data.remove(BLOCK_ID_KEY);

		if (data.isEmpty()) {
			stack.remove(DataComponentTypes.CUSTOM_DATA);
		} else {
			setCustomData(stack, data);
		}

		stack.remove(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
		stack.remove(DataComponentTypes.CUSTOM_NAME);
	}

	private static NbtCompound getCustomData(ItemStack stack) {
		NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
		return component != null ? component.copyNbt() : new NbtCompound();
	}

	private static void setCustomData(ItemStack stack, NbtCompound data) {
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
	}
}