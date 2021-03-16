package com.simibubi.create.content.logistics.block.mechanicalArm;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllBlockEntities;
import com.simibubi.create.content.contraptions.base.KineticBlockEntity;
import com.simibubi.create.content.logistics.block.mechanicalArm.ArmInteractionPoint.Mode;
import com.simibubi.create.foundation.advancement.AllTriggers;
import com.simibubi.create.foundation.block.entity.BlockEntityBehaviour;
import com.simibubi.create.foundation.block.entity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.block.entity.behaviour.scrollvalue.NamedIconOptions;
import com.simibubi.create.foundation.block.entity.behaviour.scrollvalue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widgets.InterpolatedAngle;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.CNBTHelper;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.VecHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.block.BlockState;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.Nullable;

public class ArmBlockEntity extends KineticBlockEntity {

	// Server
	List<ArmInteractionPoint> inputs;
	List<ArmInteractionPoint> outputs;
	ListTag interactionPointTag;

	// Both
	float chasedPointProgress;
	int chasedPointIndex;
	ItemStack heldItem;
	Phase phase;

	// Client
	ArmAngleTarget previousTarget;
	InterpolatedAngle lowerArmAngle;
	InterpolatedAngle upperArmAngle;
	InterpolatedAngle baseAngle;
	InterpolatedAngle headAngle;
	InterpolatedAngle clawAngle;
	float previousBaseAngle;
	boolean updateInteractionPoints;

	//
	protected ScrollOptionBehaviour<SelectionMode> selectionMode;
	protected int lastInputIndex = -1;
	protected int lastOutputIndex = -1;
	protected boolean redstoneLocked;

	enum Phase {
		SEARCH_INPUTS, MOVE_TO_INPUT, SEARCH_OUTPUTS, MOVE_TO_OUTPUT, DANCING
	}

	public ArmBlockEntity() {
		super(AllBlockEntities.MECHANICAL_ARM);
		inputs = new ArrayList<>();
		outputs = new ArrayList<>();
		interactionPointTag = new ListTag();
		heldItem = ItemStack.EMPTY;
		phase = Phase.SEARCH_INPUTS;
		baseAngle = new InterpolatedAngle();
		lowerArmAngle = new InterpolatedAngle();
		upperArmAngle = new InterpolatedAngle();
		headAngle = new InterpolatedAngle();
		clawAngle = new InterpolatedAngle();
		previousTarget = ArmAngleTarget.NO_TARGET;
		previousBaseAngle = previousTarget.baseAngle;
		updateInteractionPoints = true;
		redstoneLocked = false;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);

		selectionMode = new ScrollOptionBehaviour<SelectionMode>(SelectionMode.class,
			Lang.translate("logistics.when_multiple_outputs_available"), this, new SelectionModeValueBox());
		selectionMode.requiresWrench();
		behaviours.add(selectionMode);
	}

	@Override
	public void tick() {
		super.tick();
		initInteractionPoints();
		boolean targetReached = tickMovementProgress();

		if (world.isClient)
			return;
		if (chasedPointProgress < 1)
			return;
		
		/*if (phase == Phase.MOVE_TO_INPUT)
			collectItem();
		else if (phase == Phase.MOVE_TO_OUTPUT)
			depositItem();
		else if (phase == Phase.SEARCH_INPUTS || phase == Phase.DANCING)
			searchForItem();*/
		
		if (targetReached)
			lazyTick();
	}

	@Override
	public void lazyTick() {
		super.lazyTick();

		if (world.isClient)
			return;
		if (chasedPointProgress < .5f)
			return;
		if (phase == Phase.SEARCH_INPUTS || phase == Phase.DANCING) 
			checkForMusic();
		/*if (phase == Phase.SEARCH_OUTPUTS)
			searchForDestination();*/
	}

	private void checkForMusic() {
		boolean hasMusic = checkForMusicAmong(inputs) || checkForMusicAmong(outputs);
		if (hasMusic != (phase == Phase.DANCING)) {
			phase = hasMusic ? Phase.DANCING : Phase.SEARCH_INPUTS;
			markDirty();
			sendData();
		}
	}

	@Override
	@Environment(EnvType.CLIENT)
	public Box makeRenderBoundingBox() {
		return super.makeRenderBoundingBox().expand(3);
	}

	private boolean checkForMusicAmong(List<ArmInteractionPoint> list) {
		for (ArmInteractionPoint armInteractionPoint : list) {
			/*if (!(armInteractionPoint instanceof Jukebox))
				continue;*/
			BlockState state = world.getBlockState(armInteractionPoint.pos);
			if (state.method_28500(JukeboxBlock.HAS_RECORD).orElse(false))
				return true;
		}
		return false;
	}

	private boolean tickMovementProgress() {
		boolean targetReachedPreviously = chasedPointProgress >= 1; 
		chasedPointProgress += Math.min(256, Math.abs(getSpeed())) / 1024f;
		if (chasedPointProgress > 1)
			chasedPointProgress = 1;
		if (!world.isClient)
			return !targetReachedPreviously && chasedPointProgress >= 1;

		ArmInteractionPoint targetedInteractionPoint = getTargetedInteractionPoint();
		ArmAngleTarget previousTarget = this.previousTarget;
		ArmAngleTarget target = targetedInteractionPoint == null ? ArmAngleTarget.NO_TARGET
			: targetedInteractionPoint.getTargetAngles(pos, isOnCeiling());

		baseAngle.set(AngleHelper.angleLerp(chasedPointProgress, previousBaseAngle,
			target == ArmAngleTarget.NO_TARGET ? previousBaseAngle : target.baseAngle));

		// Arm's angles first backup to resting position and then continue
		if (chasedPointProgress < .5f)
			target = ArmAngleTarget.NO_TARGET;
		else
			previousTarget = ArmAngleTarget.NO_TARGET;
		float progress = chasedPointProgress == 1 ? 1 : (chasedPointProgress % .5f) * 2;

		lowerArmAngle.set(MathHelper.lerp(progress, previousTarget.lowerArmAngle, target.lowerArmAngle));
		upperArmAngle.set(MathHelper.lerp(progress, previousTarget.upperArmAngle, target.upperArmAngle));

		headAngle.set(AngleHelper.angleLerp(progress, previousTarget.headAngle % 360, target.headAngle % 360));
		return false;
	}

	protected boolean isOnCeiling() {
		BlockState state = getCachedState();
		return hasWorld() && state.method_28500(ArmBlock.CEILING).orElse(false);
	}

	@Nullable
	private ArmInteractionPoint getTargetedInteractionPoint() {
		if (chasedPointIndex == -1)
			return null;
		if (phase == Phase.MOVE_TO_INPUT && chasedPointIndex < inputs.size())
			return inputs.get(chasedPointIndex);
		if (phase == Phase.MOVE_TO_OUTPUT && chasedPointIndex < outputs.size())
			return outputs.get(chasedPointIndex);
		return null;
	}

	/*protected void searchForItem() {
		if (redstoneLocked)
			return;

		boolean foundInput = false;
		// for round robin, we start looking after the last used index, for default we
		// start at 0;
		int startIndex = selectionMode.get() == SelectionMode.PREFER_FIRST ? 0 : lastInputIndex + 1;

		// if we enforce round robin, only look at the next input in the list,
		// otherwise, look at all inputs
		int scanRange = selectionMode.get() == SelectionMode.FORCED_ROUND_ROBIN ? lastInputIndex + 2 : inputs.size();
		if (scanRange > inputs.size())
			scanRange = inputs.size();

		InteractionPoints: for (int i = startIndex; i < scanRange; i++) {
			ArmInteractionPoint armInteractionPoint = inputs.get(i);
			if (!armInteractionPoint.isStillValid(world))
				continue;
			for (int j = 0; j < armInteractionPoint.getSlotCount(world); j++) {
				if (getDistributableAmount(armInteractionPoint, j) == 0)
					continue;

				selectIndex(true, i);
				foundInput = true;
				break InteractionPoints;
			}
		}
		if (!foundInput && selectionMode.get() == SelectionMode.ROUND_ROBIN) {
			// if we didn't find an input, but don't want to enforce round robin, reset the
			// last index
			lastInputIndex = -1;
		}
		if (lastInputIndex == inputs.size() - 1) {
			// if we reached the last input in the list, reset the last index
			lastInputIndex = -1;
		}
	}

	protected void searchForDestination() {
		ItemStack held = heldItem.copy();

		boolean foundOutput = false;
		// for round robin, we start looking after the last used index, for default we
		// start at 0;
		int startIndex = selectionMode.get() == SelectionMode.PREFER_FIRST ? 0 : lastOutputIndex + 1;

		// if we enforce round robin, only look at the next index in the list,
		// otherwise, look at all
		int scanRange = selectionMode.get() == SelectionMode.FORCED_ROUND_ROBIN ? lastOutputIndex + 2 : outputs.size();
		if (scanRange > outputs.size())
			scanRange = outputs.size();

		for (int i = startIndex; i < scanRange; i++) {
			ArmInteractionPoint armInteractionPoint = outputs.get(i);
			if (!armInteractionPoint.isStillValid(world))
				continue;

			ItemStack remainder = armInteractionPoint.insert(world, held, true);
			if (remainder.equals(heldItem, false))
				continue;

			selectIndex(false, i);
			foundOutput = true;
			break;
		}

		if (!foundOutput && selectionMode.get() == SelectionMode.ROUND_ROBIN) {
			// if we didn't find an input, but don't want to enforce round robin, reset the
			// last index
			lastOutputIndex = -1;
		}
		if (lastOutputIndex == outputs.size() - 1) {
			// if we reached the last input in the list, reset the last index
			lastOutputIndex = -1;
		}
	}

	// input == true => select input, false => select output
	private void selectIndex(boolean input, int index) {
		phase = input ? Phase.MOVE_TO_INPUT : Phase.MOVE_TO_OUTPUT;
		chasedPointIndex = index;
		chasedPointProgress = 0;
		if (input)
			lastInputIndex = index;
		else
			lastOutputIndex = index;
		sendData();
		markDirty();
	}

	protected int getDistributableAmount(ArmInteractionPoint armInteractionPoint, int i) {
		ItemStack stack = armInteractionPoint.extract(world, i, true);
		ItemStack remainder = simulateInsertion(stack);
		return stack.getCount() - remainder.getCount();
	}

	protected void depositItem() {
		ArmInteractionPoint armInteractionPoint = getTargetedInteractionPoint();
		if (armInteractionPoint != null) {
			ItemStack toInsert = heldItem.copy();
			ItemStack remainder = armInteractionPoint.insert(world, toInsert, false);
			heldItem = remainder;
		}
		phase = heldItem.isEmpty() ? Phase.SEARCH_INPUTS : Phase.SEARCH_OUTPUTS;
		chasedPointProgress = 0;
		chasedPointIndex = -1;
		sendData();
		markDirty();

		if (!world.isClient)
			AllTriggers.triggerForNearbyPlayers(AllTriggers.MECHANICAL_ARM, world, pos, 10);
	}

	protected void collectItem() {
		ArmInteractionPoint armInteractionPoint = getTargetedInteractionPoint();
		if (armInteractionPoint != null)
			for (int i = 0; i < armInteractionPoint.getSlotCount(world); i++) {
				int amountExtracted = getDistributableAmount(armInteractionPoint, i);
				if (amountExtracted == 0)
					continue;

				heldItem = armInteractionPoint.extract(world, i, amountExtracted, false);
				phase = Phase.SEARCH_OUTPUTS;
				chasedPointProgress = 0;
				chasedPointIndex = -1;
				sendData();
				markDirty();
				return;
			}

		phase = Phase.SEARCH_INPUTS;
		chasedPointProgress = 0;
		chasedPointIndex = -1;
		sendData();
		markDirty();
	}

	private ItemStack simulateInsertion(ItemStack stack) {
		for (ArmInteractionPoint armInteractionPoint : outputs) {
			stack = armInteractionPoint.insert(world, stack, true);
			if (stack.isEmpty())
				break;
		}
		return stack;
	}*/

	public void redstoneUpdate() {
		if (world.isClient)
			return;
		boolean blockPowered = world.isReceivingRedstonePower(pos);
		if (blockPowered == redstoneLocked)
			return;
		redstoneLocked = blockPowered;
		sendData();
		/*if (!redstoneLocked)
			searchForItem();*/
	}

	protected void initInteractionPoints() {
		if (!updateInteractionPoints || interactionPointTag == null)
			return;
		if (!world.isRegionLoaded(pos, BlockPos.fromLong(getRange() + 1)))
			return;
		inputs.clear();
		outputs.clear();

		boolean hasBlazeBurner = false;
		for (Tag inbt : interactionPointTag) {
			ArmInteractionPoint point = ArmInteractionPoint.deserialize(world, pos, (CompoundTag) inbt);
			if (point == null)
				continue;
			if (point.mode == Mode.DEPOSIT)
				outputs.add(point);
			if (point.mode == Mode.TAKE)
				inputs.add(point);
			//hasBlazeBurner |= point instanceof ArmInteractionPoint.BlazeBurner;
		}

		if (!world.isClient) {
			if (outputs.size() >= 10)
				AllTriggers.triggerForNearbyPlayers(AllTriggers.ARM_MANY_TARGETS, world, pos, 5);
			if (hasBlazeBurner)
				AllTriggers.triggerForNearbyPlayers(AllTriggers.ARM_BLAZE_BURNER, world, pos, 5);
		}

		updateInteractionPoints = false;
		sendData();
		markDirty();
	}

	@Override
	public void toTag(CompoundTag compound, boolean clientPacket) {
		super.toTag(compound, clientPacket);

		if (updateInteractionPoints) {
			compound.put("InteractionPoints", interactionPointTag);

		} else {
			ListTag pointsNBT = new ListTag();
			inputs.stream()
				.map(aip -> aip.serialize(pos))
				.forEach(pointsNBT::add);
			outputs.stream()
				.map(aip -> aip.serialize(pos))
				.forEach(pointsNBT::add);
			compound.put("InteractionPoints", pointsNBT);
		}

		CNBTHelper.writeEnum(compound, "Phase", phase);
		compound.putBoolean("Powered", redstoneLocked);
		compound.put("HeldItem", heldItem.getTag());
		compound.putInt("TargetPointIndex", chasedPointIndex);
		compound.putFloat("MovementProgress", chasedPointProgress);
	}

	@Override
	protected void fromTag(BlockState state, CompoundTag compound, boolean clientPacket) {
		int previousIndex = chasedPointIndex;
		Phase previousPhase = phase;
		ListTag interactionPointTagBefore = interactionPointTag;

		super.fromTag(state, compound, clientPacket);
		heldItem = ItemStack.fromTag(compound.getCompound("HeldItem"));
		phase = CNBTHelper.readEnum(compound, "Phase", Phase.class);
		chasedPointIndex = compound.getInt("TargetPointIndex");
		chasedPointProgress = compound.getFloat("MovementProgress");
		interactionPointTag = compound.getList("InteractionPoints", NbtType.COMPOUND);
		redstoneLocked = compound.getBoolean("Powered");

		if (!clientPacket)
			return;

		boolean ceiling = isOnCeiling();
		if (interactionPointTagBefore == null || interactionPointTagBefore.size() != interactionPointTag.size())
			updateInteractionPoints = true;
		if (previousIndex != chasedPointIndex || (previousPhase != phase)) {
			ArmInteractionPoint previousPoint = null;
			if (previousPhase == Phase.MOVE_TO_INPUT && previousIndex < inputs.size())
				previousPoint = inputs.get(previousIndex);
			if (previousPhase == Phase.MOVE_TO_OUTPUT && previousIndex < outputs.size())
				previousPoint = outputs.get(previousIndex);
			previousTarget =
				previousPoint == null ? ArmAngleTarget.NO_TARGET : previousPoint.getTargetAngles(pos, ceiling);
			if (previousPoint != null)
				previousBaseAngle = previousPoint.getTargetAngles(pos, ceiling).baseAngle;
		}
	}

	public static int getRange() {
		return 5; //AllConfigs.SERVER.logistics.mechanicalArmRange.get();
	}

	@Override
	public boolean addToTooltip(List<Text> tooltip, boolean isPlayerSneaking) {
		if (super.addToTooltip(tooltip, isPlayerSneaking))
			return true;
		if (isPlayerSneaking)
			return false;
		if (!inputs.isEmpty())
			return false;
		if (!outputs.isEmpty())
			return false;

		TooltipHelper.addHint(tooltip, "hint.mechanical_arm_no_targets");
		return true;
	}

	@Override
	public boolean shouldRenderAsBE() {
		return true;
	}

	private class SelectionModeValueBox extends CenteredSideValueBoxTransform {

		public SelectionModeValueBox() {
			super((blockState, direction) -> direction != Direction.DOWN && direction != Direction.UP);
		}

		@Override
		protected Vec3d getLocalOffset(BlockState state) {
			int yPos = state.get(ArmBlock.CEILING) ? 16 - 3 : 3;
			Vec3d location = VecHelper.voxelSpace(8, yPos, 14.5);
			location = VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(getSide()), Direction.Axis.Y);
			return location;
		}

		@Override
		protected float getScale() {
			return .3f;
		}

	}

	public enum SelectionMode implements NamedIconOptions {
		ROUND_ROBIN(AllIcons.I_ARM_ROUND_ROBIN),
		FORCED_ROUND_ROBIN(AllIcons.I_ARM_FORCED_ROUND_ROBIN),
		PREFER_FIRST(AllIcons.I_ARM_PREFER_FIRST),

		;

		private final String translationKey;
		private final AllIcons icon;

		SelectionMode(AllIcons icon) {
			this.icon = icon;
			this.translationKey = "mechanical_arm.selection_mode." + Lang.asId(name());
		}

		@Override
		public AllIcons getIcon() {
			return icon;
		}

		@Override
		public String getTranslationKey() {
			return translationKey;
		}
	}

}