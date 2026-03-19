package com.shadowz.enchantedstick.mixin;

import com.shadowz.enchantedstick.InfinityItemSupport;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {
	@Unique
	private ItemStack enchantedstick$pendingPlacementStack = ItemStack.EMPTY;

	@Inject(method = "useOnBlock", at = @At("HEAD"))
	private void enchantedstick$captureInfinitePlacement(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
		ItemStack stack = context.getStack();
		this.enchantedstick$pendingPlacementStack = InfinityItemSupport.isInfiniteBlock(stack) ? stack.copy() : ItemStack.EMPTY;
	}

	@Inject(method = "useOnBlock", at = @At("RETURN"))
	private void enchantedstick$restoreInfiniteBlocks(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
		PlayerEntity player = context.getPlayer();

		if (player == null || !cir.getReturnValue().isAccepted() || this.enchantedstick$pendingPlacementStack.isEmpty()) {
			this.enchantedstick$pendingPlacementStack = ItemStack.EMPTY;
			return;
		}

		ItemStack restored = this.enchantedstick$pendingPlacementStack.copy();
		restored.setCount(1);
		player.giveItemStack(restored);
		this.enchantedstick$pendingPlacementStack = ItemStack.EMPTY;
	}
}