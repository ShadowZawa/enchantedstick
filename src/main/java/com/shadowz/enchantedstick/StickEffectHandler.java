package com.shadowz.enchantedstick;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class StickEffectHandler {
	private static final String MARKER_TAG = "enchantedstick_marker";
	private static final String MARKER_DATA_KEY = "enchantedstick_marker";
	private static final String EFFECTS_KEY = "effects";
	private static final String SOURCE_BLOCK_KEY = "source_block";
	private static final String INFECTED_BLOCK_DATA_KEY = "infected_block";
	private static final String INFECTED_SOURCE_BLOCK_KEY = "infected_source_block";
	private static final String EXPIRES_AT_KEY = "expires_at";
	private static final String HEAVY_STARTED_KEY = "heavy_started";
	private static final String HEAVY_Y_KEY = "heavy_y";
	private static final String EXPLOSION_PRIMED_KEY = "explosion_primed";
	private static final String EXPLOSION_BURSTS_REMAINING_KEY = "explosion_bursts_remaining";
	private static final String NEXT_EXPLOSION_TICK_KEY = "next_explosion_tick";
	private static final float THORNS_DAMAGE = 3.0F;
	private static final float DRAGON_THORNS_DAMAGE = 8.0F;
	private static final float EXPLOSION_POWER = 6.0F;
	private static final long BLACK_HOLE_EXPLOSION_INTERVAL_TICKS = 5L;
	private static final int END_EXPLOSION_BURST_COUNT = 10;
	private static final long END_EXPLOSION_INTERVAL_TICKS = 5L;
	private static final double DRAGON_EXPLOSION_RANGE = 12.0D;
	private static final float DRAGON_EXPLOSION_DAMAGE = 24.0F;
	private static final double BLACK_HOLE_RANGE = 100.0D;
	private static final double INFECTION_MOB_RANGE = 50.0D;
	private static final int INFECTION_LIMIT = 100;
	private static final int INFECTION_SPREAD_PER_TICK = 96;
	private static final long INFECTION_VICTIM_DURATION_TICKS = 20L * 20L;
	private static final double BOUNCE_BASE_VELOCITY = 1.05D;
	private static final double BOUNCE_STEP_VELOCITY = 0.22D;
	private static final double BOUNCE_MAX_VELOCITY = 3.25D;
	private static final int CHARGE_REPAIR_PER_SECOND = 50;
	private static final int CHARGE_LEVEL_PER_SECOND = 10;
	private static final float CHARGE_HEAL_PER_SECOND = 2.0F;
	private static final int CHARGE_FOOD_PER_SECOND = 2;
	private static final float CHARGE_SATURATION_PER_SECOND = 2.0F;
	private static final Map<String, Long> THORNS_COOLDOWNS = new HashMap<>();
	private static final Map<UUID, Integer> SPEED_PROGRESS = new HashMap<>();
	private static final Set<UUID> SPEED_ACTIVE = new HashSet<>();
	private static final Map<UUID, InfectionSpread> ACTIVE_INFECTIONS = new HashMap<>();
	private static final Map<UUID, InfectionVictimState> INFECTION_VICTIMS = new HashMap<>();
	private static final Map<net.minecraft.registry.RegistryKey<World>, Set<Long>> INFECTED_BLOCKS = new HashMap<>();
	private static final Map<UUID, Integer> BOUNCE_CHAIN = new HashMap<>();
	private static final Map<UUID, Long> BOUNCE_LAST_TICK = new HashMap<>();

	private StickEffectHandler() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register(StickEffectHandler::onAttackBlock);
		PlayerBlockBreakEvents.AFTER.register(StickEffectHandler::onBlockBroken);
		UseBlockCallback.EVENT.register(StickEffectHandler::onUseBlock);
		ServerTickEvents.END_WORLD_TICK.register(StickEffectHandler::tickWorld);
	}

	private static ActionResult onAttackBlock(PlayerEntity player, World world, Hand hand, BlockPos pos,
			Direction direction) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return ActionResult.PASS;
		}

		ItemStack stack = player.getStackInHand(hand);

		if (isCustomEnchantedStick(stack, serverWorld)) {
			markBlock(serverWorld, pos, stack);
			return ActionResult.PASS;
		}

		DisplayEntity.ItemDisplayEntity marker = findMarker(serverWorld, pos);

		if (marker == null) {
			return ActionResult.PASS;
		}

		MarkerState markerState = readMarkerState(marker);

		if (markerState.effects.contains(StickEffect.THORNS)) {
			damageEntity(serverWorld, player, THORNS_DAMAGE);
		}

		if (markerState.effects.contains(StickEffect.SHATTER)) {
			breakMarkedBlock(serverWorld, pos, player, true);
			return ActionResult.PASS;
		}

		return ActionResult.PASS;
	}

	private static void onBlockBroken(World world, PlayerEntity player, BlockPos pos, BlockState state,
			net.minecraft.block.entity.BlockEntity blockEntity) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}

		DisplayEntity.ItemDisplayEntity marker = findMarker(serverWorld, pos);
		if (marker != null) {
			MarkerState markerState = readMarkerState(marker);

			if (markerState.effects.contains(StickEffect.INFINITY)) {
				grantInfinityBlock(player, state.getBlock());
			}
		}

		if (consumeInfectedBlockMark(serverWorld, pos)) {
			grantInfectedBlock(player, state.getBlock());
		}
	}

	private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return ActionResult.PASS;
		}

		ItemStack stack = player.getStackInHand(hand);
		if (!isInfectedBlockItem(stack)) {
			return ActionResult.PASS;
		}

		Block sourceBlock = readInfectedSourceBlock(stack);
		if (sourceBlock == Blocks.AIR) {
			return ActionResult.PASS;
		}

		ItemPlacementContext placementContext = new ItemPlacementContext(player, hand, stack, hitResult);
		BlockPos hitPos = hitResult.getBlockPos();
		BlockPos placePos = world.getBlockState(hitPos).canReplace(placementContext) ? hitPos
				: hitPos.offset(hitResult.getSide());

		if (!player.canPlaceOn(placePos, hitResult.getSide(), stack)) {
			return ActionResult.FAIL;
		}

		BlockState placeState = sourceBlock.getDefaultState();
		if (!world.getBlockState(placePos).canReplace(placementContext) || !placeState.canPlaceAt(world, placePos)) {
			return ActionResult.FAIL;
		}

		if (!world.setBlockState(placePos, placeState, Block.NOTIFY_ALL)) {
			return ActionResult.FAIL;
		}

		markInfectedBlock(serverWorld, placePos);
		createInfectionMarker(serverWorld, placePos, sourceBlock);

		if (!player.isCreative()) {
			stack.decrement(1);
		}

		world.playSound(null, placePos, placeState.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
		return ActionResult.SUCCESS;
	}

	private static void tickWorld(ServerWorld world) {
		long worldTime = world.getTime();
		List<DisplayEntity.ItemDisplayEntity> markers = collectMarkers(world);
		Set<BlockPos> chargePositions = new HashSet<>();
		Set<BlockPos> speedPositions = new HashSet<>();

		tickInfectionVictims(world, worldTime);

		for (DisplayEntity.ItemDisplayEntity marker : markers) {
			MarkerState markerState = readMarkerState(marker);
			BlockPos pos = getMarkerBlockPos(marker, markerState);

			if (!markerState.heavyStarted && world.getBlockState(pos).isAir()) {
				marker.discard();
				ACTIVE_INFECTIONS.remove(marker.getUuid());
				continue;
			}

			if (markerState.effects.isEmpty()) {
				resetMarkerVisual(marker);
				marker.setInvisible(false);
				continue;
			}

			updateMarkerVisual(marker, markerState, worldTime);

			if (shouldTriggerBlackHoleExplosionCombo(markerState, worldTime)) {
				triggerExplosion(world, pos);
				markerState.explosionPrimed = true;
				markerState.nextExplosionTick = worldTime + BLACK_HOLE_EXPLOSION_INTERVAL_TICKS;
				writeMarkerState(marker, markerState);
			}

			if (markerState.isExpired(worldTime)) {
				if (markerState.effects.contains(StickEffect.EXPLOSION) && !markerState.explosionPrimed) {
					if (handleExpiredExplosion(world, marker, markerState, pos, worldTime)) {
						continue;
					}
				}

				clearMarkerEffects(marker, markerState);
				ACTIVE_INFECTIONS.remove(marker.getUuid());
				continue;
			}

			if (markerState.effects.contains(StickEffect.SPEED)) {
				speedPositions.add(pos.toImmutable());
			}

			if (markerState.effects.contains(StickEffect.BLACK_HOLE)) {
				applyBlackHole(world, pos);
			}

			if (markerState.effects.contains(StickEffect.BOUNCE)) {
				applyBounce(world, pos);
			}

			if (markerState.effects.contains(StickEffect.SHATTER)) {
				applyShatter(world, pos);
			}

			if (markerState.effects.contains(StickEffect.CHARGE)) {
				chargePositions.add(pos.toImmutable());
			}

			if (markerState.effects.contains(StickEffect.INFECTION) && markerState.sourceBlock != Blocks.AIR) {
				applyInfection(world, marker, pos, markerState.sourceBlock, worldTime);
			}

			if (markerState.effects.contains(StickEffect.HEAVY) && !markerState.heavyStarted) {
				startHeavy(world, marker, pos);
				markerState.heavyStarted = true;
				markerState.heavyY = pos.getY() + 0.5D;
				writeMarkerState(marker, markerState);
			}

			if (markerState.heavyStarted) {
				tickHeavyMarker(world, marker, markerState);
				writeMarkerState(marker, markerState);
			}
		}

		applyCharge(world, chargePositions, worldTime);
		applySpeed(world, speedPositions);
	}

	private static void markBlock(ServerWorld world, BlockPos pos, ItemStack stack) {
		BlockState blockState = world.getBlockState(pos);

		if (blockState.isAir()) {
			return;
		}

		EnumSet<StickEffect> newEffects = getStickEffects(stack, world);

		if (newEffects.isEmpty()) {
			return;
		}

		DisplayEntity.ItemDisplayEntity marker = findMarker(world, pos);

		if (marker == null) {
			marker = createMarker(world, pos);
			if (marker == null) {
				return;
			}
		}

		MarkerState markerState = readMarkerState(marker);
		markerState.effects.addAll(newEffects);
		markerState.sourceBlock = blockState.getBlock();
		markerState.heavyStarted = false;
		resetExplosionState(markerState);
		configureMarkerDurations(markerState, world.getTime());
		writeMarkerState(marker, markerState);
	}

	private static void configureMarkerDurations(MarkerState markerState, long worldTime) {
		if (hasBlackHoleExplosionCombo(markerState)) {
			markerState.expiresAt = worldTime + StickEffect.BLACK_HOLE.durationTicks();
			markerState.nextExplosionTick = worldTime + StickEffect.EXPLOSION.durationTicks();
			return;
		}

		int shortestTimedDuration = markerState.effects.stream()
				.filter(StickEffect::isTimed)
				.mapToInt(StickEffect::durationTicks)
				.min()
				.orElse(0);

		markerState.expiresAt = shortestTimedDuration > 0 ? worldTime + shortestTimedDuration : -1L;
	}

	private static DisplayEntity.ItemDisplayEntity createMarker(ServerWorld world, BlockPos pos) {
		DisplayEntity.ItemDisplayEntity display = EntityType.ITEM_DISPLAY.create(world);

		if (display == null) {
			return null;
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
		display.addCommandTag(MARKER_TAG);

		if (!world.spawnEntity(display)) {
			return null;
		}

		return display;
	}

	private static List<DisplayEntity.ItemDisplayEntity> collectMarkers(ServerWorld world) {
		List<DisplayEntity.ItemDisplayEntity> markers = new ArrayList<>();

		for (Entity entity : world.iterateEntities()) {
			if (entity instanceof DisplayEntity.ItemDisplayEntity display
					&& display.getCommandTags().contains(MARKER_TAG)) {
				markers.add(display);
			}
		}

		return markers;
	}

	private static DisplayEntity.ItemDisplayEntity findMarker(ServerWorld world, BlockPos pos) {
		Box box = new Box(pos).expand(0.05D);

		for (DisplayEntity.ItemDisplayEntity display : world.getEntitiesByClass(DisplayEntity.ItemDisplayEntity.class,
				box, entity -> entity.getCommandTags().contains(MARKER_TAG))) {
			if (BlockPos.ofFloored(display.getPos()).equals(pos)) {
				return display;
			}
		}

		return null;
	}

	private static boolean isCustomEnchantedStick(ItemStack stack, ServerWorld world) {
		return !getStickEffects(stack, world).isEmpty();
	}

	private static EnumSet<StickEffect> getStickEffects(ItemStack stack, ServerWorld world) {
		EnumSet<StickEffect> effects = EnumSet.noneOf(StickEffect.class);

		if (!stack.isOf(Items.STICK)) {
			return effects;
		}

		Registry<Enchantment> enchantmentRegistry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);

		for (StickEffect effect : StickEffect.orderedValues()) {
			Optional<RegistryEntry.Reference<Enchantment>> entry = enchantmentRegistry
					.getEntry(effect.enchantmentKey());

			if (entry.isPresent() && EnchantmentHelper.getLevel(entry.get(), stack) > 0) {
				effects.add(effect);
			}
		}

		return effects;
	}

	private static void applyBlackHole(ServerWorld world, BlockPos pos) {
		Vec3d center = pos.toCenterPos();
		Box box = new Box(pos).expand(BLACK_HOLE_RANGE);

		for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, box,
				living -> !(living instanceof PlayerEntity))) {
			Vec3d direction = center.subtract(entity.getPos());
			double distance = Math.max(direction.length(), 0.001D);
			Vec3d velocity = direction.normalize()
					.multiply(Math.min(2.0D, 1.0D + (BLACK_HOLE_RANGE - distance) / BLACK_HOLE_RANGE));
			entity.addVelocity(velocity.x, velocity.y * 0.35D, velocity.z);
			entity.velocityModified = true;
		}
	}

	private static void applyBounce(ServerWorld world, BlockPos pos) {
		Box box = new Box(pos).expand(0.15D, 1.0D, 0.15D);
		long worldTime = world.getTime();

		for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, box,
				living -> living.isOnGround() && isStandingOnMarkedBlock(living, pos))) {
			if (entity.getVelocity().y <= 0.08D) {
				if (BOUNCE_LAST_TICK.getOrDefault(entity.getUuid(), -1L) == worldTime) {
					continue;
				}

				int chain = BOUNCE_CHAIN.getOrDefault(entity.getUuid(), 0) + 1;
				BOUNCE_CHAIN.put(entity.getUuid(), chain);
				BOUNCE_LAST_TICK.put(entity.getUuid(), worldTime);
				Vec3d currentVelocity = entity.getVelocity();
				double bounceVelocity = Math.min(BOUNCE_MAX_VELOCITY,
						BOUNCE_BASE_VELOCITY + (chain - 1) * BOUNCE_STEP_VELOCITY);
				entity.setVelocity(currentVelocity.x, bounceVelocity, currentVelocity.z);
				entity.velocityModified = true;
			}
		}
	}

	private static void applyShatter(ServerWorld world, BlockPos pos) {
		Box box = new Box(pos).expand(0.1D, 1.2D, 0.1D);

		for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, box,
				living -> living.isOnGround() && living.getY() >= pos.getY() + 0.9D)) {
			breakMarkedBlock(world, pos, null, false);
			return;
		}
	}

	private static void applyCharge(ServerWorld world, Set<BlockPos> chargePositions, long worldTime) {
		if (worldTime % 20L != 0L) {
			return;
		}

		if (chargePositions.isEmpty()) {
			return;
		}

		for (PlayerEntity player : world.getPlayers(player -> findTrackedFootBlock(player, chargePositions) != null)) {
			player.heal(CHARGE_HEAL_PER_SECOND);
			player.addExperience(CHARGE_LEVEL_PER_SECOND);

			HungerManager hungerManager = player.getHungerManager();
			hungerManager.add(CHARGE_FOOD_PER_SECOND, CHARGE_SATURATION_PER_SECOND);

			for (ItemStack armorStack : player.getArmorItems()) {
				repairItem(armorStack, CHARGE_REPAIR_PER_SECOND);
			}

			repairItem(player.getMainHandStack(), CHARGE_REPAIR_PER_SECOND);
			repairItem(player.getOffHandStack(), CHARGE_REPAIR_PER_SECOND);
		}
	}

	private static void applyInfection(ServerWorld world, DisplayEntity.ItemDisplayEntity marker, BlockPos origin,
			Block sourceBlock, long worldTime) {
		InfectionSpread spread = ACTIVE_INFECTIONS.computeIfAbsent(marker.getUuid(),
				unused -> new InfectionSpread(origin));
		BlockState sourceState = sourceBlock.getDefaultState();
		markInfectedBlock(world, origin);
		int processed = 0;

		while (!spread.frontier.isEmpty() && processed < INFECTION_SPREAD_PER_TICK) {
			BlockPos current = spread.frontier.removeFirst();

			for (Direction direction : Direction.values()) {
				BlockPos next = current.offset(direction);
				long encoded = next.asLong();

				if (!spread.visited.add(encoded)) {
					continue;
				}

				if (manhattanDistance(origin, next) > INFECTION_LIMIT) {
					continue;
				}

				BlockState currentState = world.getBlockState(next);

				if (currentState.isAir()) {
					continue;
				}

				markInfectedBlock(world, next);

				if (currentState.getBlock() != sourceBlock) {
					world.setBlockState(next, sourceState, Block.NOTIFY_ALL);
				}

				spread.frontier.addLast(next.toImmutable());
				processed++;
			}
		}

		Box range = new Box(origin).expand(INFECTION_MOB_RANGE);

		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, range, entity -> entity
				.squaredDistanceTo(origin.toCenterPos()) <= INFECTION_MOB_RANGE * INFECTION_MOB_RANGE)) {
			INFECTION_VICTIMS.compute(mob.getUuid(), (uuid, existing) -> {
				if (existing == null) {
					return new InfectionVictimState(world.getRegistryKey(), mob.isAiDisabled(), mob.getCustomName(),
							mob.isCustomNameVisible(), worldTime + INFECTION_VICTIM_DURATION_TICKS);
				}

				existing.expireAt = worldTime + INFECTION_VICTIM_DURATION_TICKS;
				return existing;
			});

			mob.setAiDisabled(true);
			mob.setCustomName(Text.literal("被感染的生物"));
			mob.setCustomNameVisible(true);
		}
	}

	private static void startHeavy(ServerWorld world, DisplayEntity.ItemDisplayEntity marker, BlockPos pos) {
		BlockState state = world.getBlockState(pos);

		if (state.isAir()) {
			return;
		}

		setMarkerDisplayStack(marker, createMarkerDisplayStack(state.getBlock()));
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
	}

	private static void tickHeavyMarker(ServerWorld world, DisplayEntity.ItemDisplayEntity marker,
			MarkerState markerState) {
		markerState.heavyY -= 0.8D;
		marker.refreshPositionAndAngles(marker.getX(), markerState.heavyY, marker.getZ(), marker.getYaw(),
				marker.getPitch());

		BlockPos fallingPos = BlockPos.ofFloored(marker.getX(), markerState.heavyY - 0.5D, marker.getZ());

		for (int offset = 0; offset <= 1; offset++) {
			BlockPos destroyPos = fallingPos.up(offset);
			if (!world.getBlockState(destroyPos).isAir()) {
				world.setBlockState(destroyPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
			}
		}

		if (markerState.heavyY <= -500.0D) {
			marker.discard();
			ACTIVE_INFECTIONS.remove(marker.getUuid());
		}
	}

	private static void tickInfectionVictims(ServerWorld world, long worldTime) {
		Iterator<Map.Entry<UUID, InfectionVictimState>> iterator = INFECTION_VICTIMS.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<UUID, InfectionVictimState> entry = iterator.next();
			InfectionVictimState state = entry.getValue();

			if (!state.worldKey.equals(world.getRegistryKey()) || worldTime < state.expireAt) {
				continue;
			}

			Entity entity = world.getEntity(entry.getKey());

			if (entity instanceof MobEntity mob) {
				mob.setAiDisabled(state.aiDisabled);
				mob.setCustomName(state.customName);
				mob.setCustomNameVisible(state.customNameVisible);
			}

			iterator.remove();
		}

		BOUNCE_CHAIN.entrySet()
				.removeIf(entry -> worldTime - BOUNCE_LAST_TICK.getOrDefault(entry.getKey(), -100L) > 20L);
		BOUNCE_LAST_TICK.entrySet().removeIf(entry -> worldTime - entry.getValue() > 20L);
	}

	private static void applySpeed(ServerWorld world, Set<BlockPos> speedPositions) {
		for (PlayerEntity player : world.getPlayers()) {
			if (findTrackedFootBlock(player, speedPositions) == null) {
				SPEED_PROGRESS.remove(player.getUuid());
				SPEED_ACTIVE.remove(player.getUuid());
				continue;
			}

			int progress = SPEED_PROGRESS.getOrDefault(player.getUuid(), 0) + 1;
			SPEED_PROGRESS.put(player.getUuid(), progress);
			SPEED_ACTIVE.add(player.getUuid());
			player.addStatusEffect(
					new StatusEffectInstance(StatusEffects.SPEED, 5, Math.min(progress / 20, 4), true, false, true));
		}
	}

	private static void breakMarkedBlock(ServerWorld world, BlockPos pos, PlayerEntity player, boolean dropStacks) {
		BlockState state = world.getBlockState(pos);

		if (state.isAir()) {
			return;
		}

		DisplayEntity.ItemDisplayEntity marker = findMarker(world, pos);
		MarkerState markerState = marker != null ? readMarkerState(marker) : MarkerState.empty();

		if (dropStacks) {
			Block.dropStacks(state, world, pos, world.getBlockEntity(pos), player,
					player != null ? player.getMainHandStack() : ItemStack.EMPTY);
		}

		if (markerState.effects.contains(StickEffect.INFINITY) && player != null) {
			grantInfinityBlock(player, state.getBlock());
		}

		world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		world.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0F, 1.0F);
	}

	private static void grantInfinityBlock(PlayerEntity player, Block block) {
		player.giveItemStack(InfinityItemSupport.createInfiniteBlockStack(block));
	}

	private static void grantInfectedBlock(PlayerEntity player, Block block) {
		if (block.asItem() == Items.AIR) {
			return;
		}

		ItemStack infectedStack = new ItemStack(block.asItem());
		infectedStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§r受到感染的方塊"));

		NbtCompound infectedData = new NbtCompound();
		infectedData.putBoolean(INFECTED_BLOCK_DATA_KEY, true);
		infectedData.putString(INFECTED_SOURCE_BLOCK_KEY, Registries.BLOCK.getId(block).toString());
		infectedStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(infectedData));

		player.giveItemStack(infectedStack);
	}

	private static boolean isInfectedBlockItem(ItemStack stack) {
		NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (customData == null) {
			return false;
		}

		NbtCompound data = customData.copyNbt();
		return data.getBoolean(INFECTED_BLOCK_DATA_KEY);
	}

	private static Block readInfectedSourceBlock(ItemStack stack) {
		NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (customData == null) {
			return Blocks.AIR;
		}

		NbtCompound data = customData.copyNbt();
		if (!data.contains(INFECTED_SOURCE_BLOCK_KEY, NbtElement.STRING_TYPE)) {
			return Blocks.AIR;
		}

		Identifier id = Identifier.tryParse(data.getString(INFECTED_SOURCE_BLOCK_KEY));
		if (id == null || !Registries.BLOCK.containsId(id)) {
			return Blocks.AIR;
		}

		return Registries.BLOCK.get(id);
	}

	private static void markInfectedBlock(ServerWorld world, BlockPos pos) {
		INFECTED_BLOCKS.computeIfAbsent(world.getRegistryKey(), unused -> new HashSet<>()).add(pos.asLong());
	}

	private static boolean consumeInfectedBlockMark(ServerWorld world, BlockPos pos) {
		Set<Long> marked = INFECTED_BLOCKS.get(world.getRegistryKey());
		if (marked == null) {
			return false;
		}

		boolean consumed = marked.remove(pos.asLong());
		if (marked.isEmpty()) {
			INFECTED_BLOCKS.remove(world.getRegistryKey());
		}

		return consumed;
	}

	private static void createInfectionMarker(ServerWorld world, BlockPos pos, Block sourceBlock) {
		DisplayEntity.ItemDisplayEntity marker = findMarker(world, pos);
		if (marker == null) {
			marker = createMarker(world, pos);
			if (marker == null) {
				return;
			}
		}

		MarkerState markerState = readMarkerState(marker);
		markerState.effects.add(StickEffect.INFECTION);
		markerState.sourceBlock = sourceBlock;
		markerState.heavyStarted = false;
		resetExplosionState(markerState);
		markerState.expiresAt = world.getTime() + StickEffect.INFECTION.durationTicks();
		writeMarkerState(marker, markerState);
	}

	private static boolean handleExpiredExplosion(ServerWorld world, DisplayEntity.ItemDisplayEntity marker,
			MarkerState markerState, BlockPos pos, long worldTime) {
		if (hasBlackHoleExplosionCombo(markerState)) {
			return false;
		}

		if (world.getRegistryKey() != World.END) {
			triggerExplosion(world, pos);
			return false;
		}

		if (markerState.explosionBurstsRemaining <= 0) {
			markerState.explosionBurstsRemaining = END_EXPLOSION_BURST_COUNT;
			markerState.nextExplosionTick = worldTime;
		}

		if (worldTime < markerState.nextExplosionTick) {
			writeMarkerState(marker, markerState);
			return true;
		}

		triggerExplosion(world, pos);
		markerState.explosionBurstsRemaining--;

		if (markerState.explosionBurstsRemaining > 0) {
			markerState.nextExplosionTick = worldTime + END_EXPLOSION_INTERVAL_TICKS;
			writeMarkerState(marker, markerState);
			return true;
		}

		return false;
	}

	private static void triggerExplosion(ServerWorld world, BlockPos pos) {
		world.createExplosion(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
				EXPLOSION_POWER,
				World.ExplosionSourceType.BLOCK);
		applyDragonExplosionDamage(world, pos);
	}

	private static boolean shouldTriggerBlackHoleExplosionCombo(MarkerState markerState, long worldTime) {
		return hasBlackHoleExplosionCombo(markerState)
				&& markerState.nextExplosionTick >= 0L
				&& worldTime >= markerState.nextExplosionTick;
	}

	private static boolean hasBlackHoleExplosionCombo(MarkerState markerState) {
		return markerState.effects.contains(StickEffect.BLACK_HOLE)
				&& markerState.effects.contains(StickEffect.EXPLOSION);
	}

	private static void applyDragonExplosionDamage(ServerWorld world, BlockPos pos) {
		Vec3d center = pos.toCenterPos();
		// 龍非常大，將搜尋方框設為 20x20x20
		Box searchBox = new Box(pos).expand(10.0);

		// 使用 getOtherEntities(null, ...) 抓取範圍內「任何」實體
		List<Entity> allEntities = world.getOtherEntities(null, searchBox);

		Set<EnderDragonEntity> damagedDragons = new HashSet<>();

		for (Entity entity : allEntities) {
			EnderDragonEntity dragon = null;

			// 核心邏輯：判斷它是龍的主體還是部位
			if (entity instanceof EnderDragonEntity d) {
				dragon = d;
			} else if (entity instanceof EnderDragonPart part) {
				dragon = part.owner;
			}

			if (dragon != null && dragon.isAlive() && !damagedDragons.contains(dragon)) {
				// 計算這個實體（部位或主體）與中心的距離
				double distSq = entity.squaredDistanceTo(center);

				// 距離檢查（32 的平方是 1024）
				if (distSq <= 1024) {
					// 強制執行傷害邏輯
					damageEntity(world, dragon, 1000.0F);

					// 標記已受傷
					damagedDragons.add(dragon);

					// 100% 會在控制台顯示的訊息
					System.out.println(">>> [DEBUG] damage success: " + entity.getType().getName().getString());

					// 視覺回饋：在命中的位置生成巨大爆炸粒子
					world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
							entity.getX(), entity.getY(), entity.getZ(), 1, 0, 0, 0, 0);
				}
			}
		}
	}

	private static void repairItem(ItemStack stack, int amount) {
		if (stack.isEmpty() || !stack.isDamageable() || !stack.isDamaged()) {
			return;
		}

		stack.setDamage(Math.max(0, stack.getDamage() - amount));
	}

	private static int manhattanDistance(BlockPos first, BlockPos second) {
		return Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY())
				+ Math.abs(first.getZ() - second.getZ());
	}

	private static BlockPos getStandingBlockPos(Entity entity) {
		return BlockPos.ofFloored(entity.getX(), entity.getBoundingBox().minY - 0.1D, entity.getZ());
	}

	private static boolean isTouchingMarkedBlock(LivingEntity entity, BlockPos pos) {
		if (isStandingOnMarkedBlock(entity, pos)) {
			return true;
		}

		Box box = entity.getBoundingBox();
		int minX = (int) Math.floor(box.minX);
		int maxX = (int) Math.floor(box.maxX - 1.0E-6D);
		int minZ = (int) Math.floor(box.minZ);
		int maxZ = (int) Math.floor(box.maxZ - 1.0E-6D);
		int feetY = (int) Math.floor(box.minY - 0.05D);

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				if (pos.getX() == x && pos.getY() == feetY && pos.getZ() == z) {
					return true;
				}
			}
		}

		return box.intersects(new Box(pos).expand(0.3D));
	}

	private static boolean isStandingOnMarkedBlock(LivingEntity entity, BlockPos pos) {
		return getFootBlockPositions(entity).contains(pos);
	}

	private static BlockPos findTrackedFootBlock(LivingEntity entity, Set<BlockPos> trackedPositions) {
		if (trackedPositions.isEmpty()) {
			return null;
		}

		for (BlockPos footPos : getFootBlockPositions(entity)) {
			if (trackedPositions.contains(footPos)) {
				return footPos;
			}
		}

		return null;
	}

	private static Set<BlockPos> getFootBlockPositions(LivingEntity entity) {
		Set<BlockPos> positions = new HashSet<>();
		Box box = entity.getBoundingBox();
		int minX = (int) Math.floor(box.minX + 1.0E-6D);
		int maxX = (int) Math.floor(box.maxX - 1.0E-6D);
		int minZ = (int) Math.floor(box.minZ + 1.0E-6D);
		int maxZ = (int) Math.floor(box.maxZ - 1.0E-6D);
		int feetY = (int) Math.floor(box.minY - 0.2D);

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				positions.add(new BlockPos(x, feetY, z));
			}
		}

		positions.add(getStandingBlockPos(entity));
		return positions;
	}

	private static BlockPos getMarkerBlockPos(DisplayEntity.ItemDisplayEntity marker, MarkerState markerState) {
		if (markerState.heavyStarted && !Double.isNaN(markerState.heavyY)) {
			return BlockPos.ofFloored(marker.getX(), markerState.heavyY, marker.getZ());
		}

		return BlockPos.ofFloored(marker.getPos());
	}

	private static ItemStack createMarkerDisplayStack(Block block) {
		ItemStack stack = block.asItem() == Items.AIR ? new ItemStack(Items.BLACK_STAINED_GLASS)
				: new ItemStack(block.asItem());
		stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private static void setMarkerDisplayStack(DisplayEntity.ItemDisplayEntity marker, ItemStack newStack) {
		ItemStack currentStack = marker.getStackReference(0).get();
		NbtComponent customData = currentStack.get(DataComponentTypes.CUSTOM_DATA);

		if (customData != null) {
			newStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData.copyNbt()));
		}

		marker.getStackReference(0).set(newStack);
	}

	private static void updateMarkerVisual(DisplayEntity.ItemDisplayEntity marker, MarkerState markerState,
			long worldTime) {
		if (markerState.heavyStarted) {
			marker.setInvisible(false);
			return;
		}

		if (markerState.effects.contains(StickEffect.EXPLOSION)) {
			ItemStack flashingStack = (worldTime / 4L) % 2L == 0L ? new ItemStack(Items.TNT)
					: new ItemStack(Items.RED_STAINED_GLASS);
			flashingStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
			setMarkerDisplayStack(marker, flashingStack);
			marker.setInvisible(false);
			return;
		}

		resetMarkerVisual(marker);
		marker.setInvisible(false);
	}

	private static void resetMarkerVisual(DisplayEntity.ItemDisplayEntity marker) {
		setMarkerDisplayStack(marker, createMarkerDisplayStack(Blocks.BLACK_STAINED_GLASS));
	}

	public static boolean shouldCancelBounceFallDamage(LivingEntity entity) {
		if (!(entity.getWorld() instanceof ServerWorld world)) {
			return false;
		}

		BlockPos standingPos = getStandingBlockPos(entity);
		DisplayEntity.ItemDisplayEntity marker = findMarker(world, standingPos);

		if (marker == null) {
			return false;
		}

		MarkerState markerState = readMarkerState(marker);
		return markerState.effects.contains(StickEffect.BOUNCE);
	}

	public static void tickThornsForEntity(LivingEntity entity) {
		if (!(entity.getWorld() instanceof ServerWorld world) || !entity.isAlive()) {
			return;
		}

		// 取得生物目前的碰撞箱，並稍微擴張一點點（例如 0.1），用來偵測「觸碰」
		Box box = entity.getBoundingBox().expand(0.1);
		long worldTime = world.getTime();

		// 遍歷碰撞箱覆蓋的所有方塊座標
		for (BlockPos pos : BlockPos.iterate(
				BlockPos.ofFloored(box.minX, box.minY, box.minZ),
				BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {

			// 直接找該座標的標記
			DisplayEntity.ItemDisplayEntity marker = findMarker(world, pos);
			if (marker == null)
				continue;

			MarkerState markerState = readMarkerState(marker);
			if (markerState.effects.contains(StickEffect.THORNS)) {

				String key = pos.asLong() + ":" + entity.getUuid();
				long lastHit = THORNS_COOLDOWNS.getOrDefault(key, 0L);

				// 冷卻檢查 (20L = 1秒一次傷害)
				if (worldTime - lastHit >= 20L) {
					// 執行傷害
					float thornsDamage = entity instanceof EnderDragonEntity ? DRAGON_THORNS_DAMAGE : THORNS_DAMAGE;
					damageEntity(world, entity, thornsDamage);

					// 播放刺痛音效或粒子
					world.playSound(null, pos, SoundEvents.ENCHANT_THORNS_HIT, SoundCategory.BLOCKS, 0.5f, 1.0f);
					world.spawnParticles(ParticleTypes.ENCHANTED_HIT, pos.getX() + 0.5, pos.getY() + 1.1,
							pos.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.1);

					THORNS_COOLDOWNS.put(key, worldTime);
				}
				// 碰到一個荊棘方塊後通常不需要再檢查其他座標，直接跳出
				return;
			}
		}
	}

	private static void damageEntity(ServerWorld world, LivingEntity entity, float amount) {
		if (entity instanceof EnderDragonEntity dragon) {
			// 直接先嘗試對主體造成傷害 (部分模組或修改過的實體可能接受)
			DamageSources sources = world.getDamageSources();

// 1. 魔法傷害 (最接近你原本想寫的 MAGIC)

			boolean hit = dragon.damage(sources.explosion(null, null), 30.0f);
			
			// 如果主體免疫，嘗試暴力遍歷所有 子部件 (EnderDragonPart)
			// 注意：原版龍的 damage() 對主體通常無效，必須打部件
			// 這裡我們不使用複雜的 Accessor 或 Reflection，直接嘗試尋找部件實體
			if (!hit) {
				List<EnderDragonPart> parts = world.getEntitiesByClass(
						EnderDragonPart.class,
						dragon.getBoundingBox().expand(5.0),
						p -> p.owner == dragon);

				for (EnderDragonPart part : parts) {
					if (part.damage(sources.explosion(null, null), 30.0f)) {
						hit = true;
						// 只要有一個部件受傷就算成功，不需要每個都打
						break;
					}
				}
			}
		} else {
			entity.damage(world.getDamageSources().magic(), amount);
		}
	}
	private static void clearMarkerEffects(DisplayEntity.ItemDisplayEntity marker, MarkerState markerState) {
		markerState.effects.clear();
		markerState.expiresAt = -1L;
		markerState.heavyStarted = false;
		resetExplosionState(markerState);
		markerState.explosionPrimed = true;
		writeMarkerState(marker, markerState);
		resetMarkerVisual(marker);
		marker.setInvisible(false);
	}

	private static void resetExplosionState(MarkerState markerState) {
		markerState.explosionPrimed = false;
		markerState.explosionBurstsRemaining = 0;
		markerState.nextExplosionTick = -1L;
	}

	private static MarkerState readMarkerState(DisplayEntity.ItemDisplayEntity marker) {
		ItemStack displayStack = marker.getStackReference(0).get();
		NbtComponent customData = displayStack.get(DataComponentTypes.CUSTOM_DATA);

		if (customData == null) {
			return MarkerState.empty();
		}

		NbtCompound data = customData.copyNbt();

		if (!data.contains(MARKER_DATA_KEY, NbtElement.COMPOUND_TYPE)) {
			return MarkerState.empty();
		}

		NbtCompound markerData = data.getCompound(MARKER_DATA_KEY);
		MarkerState state = MarkerState.empty();

		if (markerData.contains(EFFECTS_KEY, NbtElement.LIST_TYPE)) {
			NbtList effectList = markerData.getList(EFFECTS_KEY, NbtElement.STRING_TYPE);

			for (int index = 0; index < effectList.size(); index++) {
				StickEffect effect = StickEffect.byId(effectList.getString(index));
				if (effect != null) {
					state.effects.add(effect);
				}
			}
		}

		if (markerData.contains(SOURCE_BLOCK_KEY, NbtElement.STRING_TYPE)) {
			Identifier identifier = Identifier.tryParse(markerData.getString(SOURCE_BLOCK_KEY));
			if (identifier != null && Registries.BLOCK.containsId(identifier)) {
				state.sourceBlock = Registries.BLOCK.get(identifier);
			}
		}

		if (markerData.contains(EXPIRES_AT_KEY, NbtElement.LONG_TYPE)) {
			state.expiresAt = markerData.getLong(EXPIRES_AT_KEY);
		}

		state.heavyStarted = markerData.getBoolean(HEAVY_STARTED_KEY);
		if (markerData.contains(HEAVY_Y_KEY, NbtElement.DOUBLE_TYPE)) {
			state.heavyY = markerData.getDouble(HEAVY_Y_KEY);
		}
		state.explosionPrimed = markerData.getBoolean(EXPLOSION_PRIMED_KEY);
		if (markerData.contains(EXPLOSION_BURSTS_REMAINING_KEY, NbtElement.INT_TYPE)) {
			state.explosionBurstsRemaining = markerData.getInt(EXPLOSION_BURSTS_REMAINING_KEY);
		}
		if (markerData.contains(NEXT_EXPLOSION_TICK_KEY, NbtElement.LONG_TYPE)) {
			state.nextExplosionTick = markerData.getLong(NEXT_EXPLOSION_TICK_KEY);
		}
		return state;
	}

	private static void writeMarkerState(DisplayEntity.ItemDisplayEntity marker, MarkerState markerState) {
		ItemStack displayStack = marker.getStackReference(0).get().copy();
		NbtComponent existing = displayStack.get(DataComponentTypes.CUSTOM_DATA);
		NbtCompound data = existing != null ? existing.copyNbt() : new NbtCompound();
		NbtCompound markerData = new NbtCompound();
		NbtList effectList = new NbtList();

		for (StickEffect effect : markerState.effects) {
			effectList.add(NbtString.of(effect.id()));
		}

		markerData.put(EFFECTS_KEY, effectList);
		markerData.putString(SOURCE_BLOCK_KEY, Registries.BLOCK.getId(markerState.sourceBlock).toString());

		if (markerState.expiresAt >= 0L) {
			markerData.putLong(EXPIRES_AT_KEY, markerState.expiresAt);
		}

		markerData.putBoolean(HEAVY_STARTED_KEY, markerState.heavyStarted);
		if (!Double.isNaN(markerState.heavyY)) {
			markerData.putDouble(HEAVY_Y_KEY, markerState.heavyY);
		}
		markerData.putBoolean(EXPLOSION_PRIMED_KEY, markerState.explosionPrimed);
		if (markerState.explosionBurstsRemaining > 0) {
			markerData.putInt(EXPLOSION_BURSTS_REMAINING_KEY, markerState.explosionBurstsRemaining);
		}
		if (markerState.nextExplosionTick >= 0L) {
			markerData.putLong(NEXT_EXPLOSION_TICK_KEY, markerState.nextExplosionTick);
		}
		data.put(MARKER_DATA_KEY, markerData);
		displayStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
		marker.getStackReference(0).set(displayStack);
	}

	private static NbtCompound createCenteredOverlayTransformationNbt() {
		NbtCompound transformation = new NbtCompound();
		transformation.put("translation", vector3fNbt(0.0F, 0.0F, 0.0F));
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

	private static final class MarkerState {
		private final EnumSet<StickEffect> effects = EnumSet.noneOf(StickEffect.class);
		private Block sourceBlock = Blocks.AIR;
		private long expiresAt = -1L;
		private boolean heavyStarted;
		private double heavyY = Double.NaN;
		private boolean explosionPrimed;
		private int explosionBurstsRemaining;
		private long nextExplosionTick = -1L;

		private static MarkerState empty() {
			return new MarkerState();
		}

		private boolean isExpired(long worldTime) {
			return this.expiresAt >= 0L && worldTime >= this.expiresAt;
		}
	}

	private static final class InfectionSpread {
		private final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
		private final Set<Long> visited = new HashSet<>();

		private InfectionSpread(BlockPos origin) {
			this.frontier.add(origin.toImmutable());
			this.visited.add(origin.asLong());
		}
	}

	private static final class InfectionVictimState {
		private final net.minecraft.registry.RegistryKey<World> worldKey;
		private final boolean aiDisabled;
		private final Text customName;
		private final boolean customNameVisible;
		private long expireAt;

		private InfectionVictimState(net.minecraft.registry.RegistryKey<World> worldKey, boolean aiDisabled,
				Text customName, boolean customNameVisible, long expireAt) {
			this.worldKey = worldKey;
			this.aiDisabled = aiDisabled;
			this.customName = customName;
			this.customNameVisible = customNameVisible;
			this.expireAt = expireAt;
		}
	}
}