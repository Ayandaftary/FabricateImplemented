package com.simibubi.create.modules.contraptions.base;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.simibubi.create.Create;
import com.simibubi.create.config.AllConfigs;
import com.simibubi.create.foundation.advancement.AllTriggers;
import com.simibubi.create.foundation.behaviour.base.SmartTileEntity;
import com.simibubi.create.foundation.behaviour.base.TileEntityBehaviour;
import com.simibubi.create.modules.contraptions.KineticNetwork;
import com.simibubi.create.modules.contraptions.RotationPropagator;
import com.simibubi.create.modules.contraptions.base.IRotate.SpeedLevel;
import com.simibubi.create.modules.contraptions.base.IRotate.StressImpact;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

public abstract class KineticTileEntity extends SmartTileEntity implements ITickableTileEntity {

	public @Nullable Long network;
	public @Nullable BlockPos source;
	public boolean networkDirty;

	protected KineticEffectHandler effects;
	protected float speed;
	protected float capacity;
	protected float stress;
	protected boolean overStressed;

	private int flickerTally;
	private int validationCountdown;

	public KineticTileEntity(TileEntityType<?> typeIn) {
		super(typeIn);
		effects = new KineticEffectHandler(this);
	}

	@Override
	public void initialize() {
		super.initialize();
		if (!hasNetwork())
			return;

		KineticNetwork network = getOrCreateNetwork();
		if (!network.initialized)
			network.initFromTE(capacity, stress);
		network.addSilently(this);
	}

	@Override
	public void tick() {
		super.tick();
		effects.tick();

		if (world.isRemote)
			return;

		if (validationCountdown-- <= 0) {
			validationCountdown = AllConfigs.SERVER.kinetics.kineticValidationFrequency.get();
			validateKinetics();
		}

		if (getFlickerScore() > 0)
			flickerTally = getFlickerScore() - 1;

		if (networkDirty) {
			if (hasNetwork())
				getOrCreateNetwork().updateNetwork();
			networkDirty = false;
		}
	}

	private void validateKinetics() {
		if (hasSource()) {
			if (!hasNetwork()) {
				removeSource();
				return;
			}

			if (!world.isBlockPresent(source))
				return;

			KineticTileEntity sourceTe = (KineticTileEntity) world.getTileEntity(source);
			if (sourceTe == null || sourceTe.speed == 0) {
				removeSource();
				detachKinetics();
				return;
			}

			return;
		}

		if (speed != 0) {
			if (getGeneratedSpeed() == 0)
				speed = 0;
		}
	}

	public void updateStressFromNetwork(float maxStress, float currentStress) {
		networkDirty = false;
		this.capacity = maxStress;
		this.stress = currentStress;
		boolean overStressed = maxStress < currentStress && StressImpact.isEnabled();

		if (overStressed != this.overStressed) {
			if (speed != 0 && overStressed)
				AllTriggers.triggerForNearbyPlayers(AllTriggers.OVERSTRESSED, world, pos, 8);
			float prevSpeed = getSpeed();
			this.overStressed = overStressed;
			onSpeedChanged(prevSpeed);
			sendData();
		}
	}

	public float getAddedStressCapacity() {
		Map<ResourceLocation, ConfigValue<Double>> capacityMap = AllConfigs.SERVER.kinetics.stressValues.capacities;
		ResourceLocation path = getBlockState().getBlock().getRegistryName();
		if (!capacityMap.containsKey(path))
			return 0;
		return capacityMap.get(path).get().floatValue();
	}

	public float getStressApplied() {
		Map<ResourceLocation, ConfigValue<Double>> stressEntries = AllConfigs.SERVER.kinetics.stressValues.impacts;
		ResourceLocation path = getBlockState().getBlock().getRegistryName();
		if (!stressEntries.containsKey(path))
			return 1;
		return stressEntries.get(path).get().floatValue();
	}

	public void onSpeedChanged(float previousSpeed) {
		boolean fromOrToZero = (previousSpeed == 0) != (getSpeed() == 0);
		boolean directionSwap = !fromOrToZero && Math.signum(previousSpeed) != Math.signum(getSpeed());
		if (fromOrToZero || directionSwap) {
			flickerTally = getFlickerScore() + 5;
		}
	}

	@Override
	public void remove() {
		if (!world.isRemote) {
			if (hasNetwork())
				getOrCreateNetwork().remove(this);
			detachKinetics();
		}
		super.remove();
	}

	@Override
	public CompoundNBT write(CompoundNBT compound) {
		compound.putFloat("Speed", speed);

		if (hasSource())
			compound.put("Source", NBTUtil.writeBlockPos(source));

		if (hasNetwork()) {
			CompoundNBT networkTag = new CompoundNBT();
			networkTag.putLong("Id", network);
			networkTag.putFloat("Stress", stress);
			networkTag.putFloat("Capacity", capacity);
			compound.put("Network", networkTag);
		}

		return super.write(compound);
	}

	@Override
	public void read(CompoundNBT compound) {
		speed = compound.getFloat("Speed");

		source = null;
		network = null;
		overStressed = false;
		stress = 0;
		capacity = 0;

		if (compound.contains("Source"))
			source = NBTUtil.readBlockPos(compound.getCompound("Source"));

		if (compound.contains("Network")) {
			CompoundNBT networkTag = compound.getCompound("Network");
			network = networkTag.getLong("Id");
			stress = networkTag.getFloat("Stress");
			capacity = networkTag.getFloat("Capacity");
			overStressed = capacity < stress && StressImpact.isEnabled();
		}

		super.read(compound);
	}

	@Override
	public void readClientUpdate(CompoundNBT tag) {
		boolean overStressedBefore = overStressed;
		super.readClientUpdate(tag);
		if (overStressedBefore != overStressed && speed != 0)
			effects.triggerOverStressedEffect();
	}

	public float getGeneratedSpeed() {
		return 0;
	}

	public boolean isSource() {
		return getGeneratedSpeed() != 0;
	}

	public float getSpeed() {
		if (overStressed)
			return 0;
		return getTheoreticalSpeed();
	}

	public float getTheoreticalSpeed() {
		return speed;
	}

	public void setSpeed(float speed) {
		this.speed = speed;
	}

	public boolean hasSource() {
		return source != null;
	}

	public void setSource(BlockPos source) {
		this.source = source;
		if (world == null || world.isRemote)
			return;

		KineticTileEntity sourceTe = (KineticTileEntity) world.getTileEntity(source);
		if (sourceTe == null) {
			removeSource();
			return;
		}

		setNetwork(sourceTe.network);
	}

	public void removeSource() {
		float prevSpeed = getSpeed();

		speed = 0;
		source = null;
		setNetwork(null);

		onSpeedChanged(prevSpeed);
	}

	public void setNetwork(@Nullable Long networkIn) {
		if (network == networkIn)
			return;
		if (network != null)
			getOrCreateNetwork().remove(this);

		network = networkIn;

		if (networkIn == null)
			return;

		network = networkIn;
		KineticNetwork network = getOrCreateNetwork();
		network.initialized = true;
		network.add(this);
	}

	public KineticNetwork getOrCreateNetwork() {
		return Create.torquePropagator.getNetworkFor(this);
	}

	public boolean hasNetwork() {
		return network != null;
	}

	public void attachKinetics() {
		RotationPropagator.handleAdded(world, pos, this);
	}

	public void detachKinetics() {
		RotationPropagator.handleRemoved(world, pos, this);
	}

	public boolean isSpeedRequirementFulfilled() {
		BlockState state = getBlockState();
		if (!(getBlockState().getBlock() instanceof IRotate))
			return true;
		IRotate def = (IRotate) state.getBlock();
		SpeedLevel minimumRequiredSpeedLevel = def.getMinimumRequiredSpeedLevel();
		if (minimumRequiredSpeedLevel == null)
			return true;
		if (minimumRequiredSpeedLevel == SpeedLevel.MEDIUM)
			return Math.abs(getSpeed()) >= AllConfigs.SERVER.kinetics.mediumSpeed.get();
		if (minimumRequiredSpeedLevel == SpeedLevel.FAST)
			return Math.abs(getSpeed()) >= AllConfigs.SERVER.kinetics.fastSpeed.get();
		return true;
	}

	public static void switchToBlockState(World world, BlockPos pos, BlockState state) {
		if (world.isRemote)
			return;
		TileEntity tileEntityIn = world.getTileEntity(pos);
		if (!(tileEntityIn instanceof KineticTileEntity))
			return;
		KineticTileEntity tileEntity = (KineticTileEntity) tileEntityIn;
		if (tileEntity.hasNetwork())
			tileEntity.getOrCreateNetwork().remove(tileEntity);
		tileEntity.detachKinetics();
		tileEntity.removeSource();
		world.setBlockState(pos, state, 3);
		tileEntity.attachKinetics();
	}

	@Override
	public void addBehaviours(List<TileEntityBehaviour> behaviours) {
	}

	@Override
	public boolean hasFastRenderer() {
		return true;
	}

	public int getFlickerScore() {
		return flickerTally;
	}

}
