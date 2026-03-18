package com.shadowz.enchantedstick;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class StickEffectHandler {
	private static final Map<AuraKey, AuraData> ACTIVE_AURAS = new HashMap<>();

	private StickEffectHandler() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register(StickEffectHandler::onAttackBlock);
		ServerTickEvents.END_WORLD_TICK.register(StickEffectHandler::tickDisplays);
	}

	private static ActionResult onAttackBlock(PlayerEntity player, World world, Hand hand, BlockPos pos, net.minecraft.util.math.Direction direction) {
		if (world.isClient()) {
			return ActionResult.PASS;
		}

		ItemStack stack = player.getStackInHand(hand);

		if (!isCustomEnchantedStick(stack, (ServerWorld) world)) {
			return ActionResult.PASS;
		}

		spawnDisplay((ServerWorld) world, pos);
		return ActionResult.PASS;
	}

	private static boolean isCustomEnchantedStick(ItemStack stack, ServerWorld world) {
		if (!stack.isOf(Items.STICK)) {
			return false;
		}

		Registry<Enchantment> enchantmentRegistry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);

		for (RegistryKey<Enchantment> key : StickEnchantments.ALL) {
			Optional<RegistryEntry.Reference<Enchantment>> entry = enchantmentRegistry.getEntry(key);

			if (entry.isPresent() && EnchantmentHelper.getLevel(entry.get(), stack) > 0) {
				return true;
			}
		}

		return false;
	}

	private static void spawnDisplay(ServerWorld world, BlockPos pos) {
		AuraKey key = new AuraKey(world.getRegistryKey(), pos.toImmutable());
		AuraData existing = ACTIVE_AURAS.get(key);

		if (existing != null) {
			Entity existingEntity = world.getEntity(existing.entityUuid);

			if (existingEntity != null && existingEntity.isAlive()) {
				return;
			}

			ACTIVE_AURAS.remove(key);
		}

		DisplayEntity.ItemDisplayEntity display = EntityType.ITEM_DISPLAY.create(world);

		if (display == null) {
			return;
		}

		ItemStack glintStack = new ItemStack(Items.BLACK_STAINED_GLASS);
		glintStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		display.getStackReference(0).set(glintStack);

		NbtCompound displayNbt = new NbtCompound();
		display.writeNbt(displayNbt);
		displayNbt.putString("item_display", "fixed");
		displayNbt.put("transformation", createCenteredOverlayTransformationNbt());
		display.readNbt(displayNbt);

		display.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.0F, 0.0F);
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.addCommandTag("enchanted_glint");

		if (world.spawnEntity(display)) {
			ACTIVE_AURAS.put(key, new AuraData(display.getUuid()));
		}
	}

	private static void tickDisplays(ServerWorld world) {
		Iterator<Map.Entry<AuraKey, AuraData>> iterator = ACTIVE_AURAS.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<AuraKey, AuraData> entry = iterator.next();
			AuraKey key = entry.getKey();
			AuraData aura = entry.getValue();

			if (!key.worldKey.equals(world.getRegistryKey())) {
				continue;
			}

			Entity entity = world.getEntity(aura.entityUuid);

			if (entity == null || !entity.isAlive()) {
				iterator.remove();
				continue;
			}

			if (world.getBlockState(key.blockPos).isAir()) {
				entity.discard();
				iterator.remove();
			}
		}
	}

	private static NbtCompound createCenteredOverlayTransformationNbt() {
		NbtCompound transformation = new NbtCompound();
		transformation.put("translation", vector3fNbt(0.0F, 0.0F, 0.0F));
		// FIXED mode block-items render at roughly half block scale, so we upscale to cover the full block.
		transformation.put("scale", vector3fNbt(2.02F, 2.02F, 2.02F));
		transformation.put("left_rotation", quaternionIdentityNbt());
		transformation.put("right_rotation", quaternionIdentityNbt());
		return transformation;
	}

	private static NbtList vector3fNbt(float x, float y, float z) {
		NbtList list = new NbtList();
		list.add(NbtFloat.of(x));
		list.add(NbtFloat.of(y));
		list.add(NbtFloat.of(z));
		return list;
	}

	private static NbtList quaternionIdentityNbt() {
		NbtList list = new NbtList();
		list.add(NbtFloat.of(0.0F));
		list.add(NbtFloat.of(0.0F));
		list.add(NbtFloat.of(0.0F));
		list.add(NbtFloat.of(1.0F));
		return list;
	}

	private record AuraKey(RegistryKey<World> worldKey, BlockPos blockPos) {
	}

	private static final class AuraData {
		private final UUID entityUuid;

		private AuraData(UUID entityUuid) {
			this.entityUuid = entityUuid;
		}
	}
}