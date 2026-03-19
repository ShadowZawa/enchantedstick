package com.shadowz.enchantedstick.mixin;

import com.shadowz.enchantedstick.InfinityItemSupport;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
	@Unique
	private ItemStack enchantedstick$pendingInfiniteDrop = ItemStack.EMPTY;

	@Shadow
	@Final
	public PlayerEntity player;

	@Inject(method = "dropSelectedItem", at = @At("HEAD"))
	private void enchantedstick$captureInfiniteDrop(boolean entireStack, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack selected = this.player.getMainHandStack();
		this.enchantedstick$pendingInfiniteDrop = InfinityItemSupport.isInfiniteBlock(selected) ? selected.copy() : ItemStack.EMPTY;
	}

	@Inject(method = "dropSelectedItem", at = @At("RETURN"))
	private void enchantedstick$restoreDroppedInfiniteBlocks(boolean entireStack, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack droppedStack = cir.getReturnValue();

		if (droppedStack == null || droppedStack.isEmpty() || this.enchantedstick$pendingInfiniteDrop.isEmpty()) {
			this.enchantedstick$pendingInfiniteDrop = ItemStack.EMPTY;
			return;
		}

		InfinityItemSupport.stripInfinityData(droppedStack);

		ItemStack restored = this.enchantedstick$pendingInfiniteDrop.copy();
		restored.setCount(entireStack ? this.enchantedstick$pendingInfiniteDrop.getCount() : 1);
		this.player.giveItemStack(restored);
		this.enchantedstick$pendingInfiniteDrop = ItemStack.EMPTY;
	}
}