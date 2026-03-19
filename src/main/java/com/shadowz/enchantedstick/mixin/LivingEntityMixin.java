package com.shadowz.enchantedstick.mixin;

import com.shadowz.enchantedstick.StickEffectHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void enchantedstick$tickThorns(CallbackInfo ci) {
		StickEffectHandler.tickThornsForEntity((LivingEntity) (Object) this);
	}

	@Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
	private void enchantedstick$cancelBounceFallDamage(float fallDistance, float damagePerDistance, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
		if (StickEffectHandler.shouldCancelBounceFallDamage((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}