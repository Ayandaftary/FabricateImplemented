package com.simibubi.create.content.contraptions.components.clock;

import static com.simibubi.create.foundation.utility.AngleHelper.deg;
import static com.simibubi.create.foundation.utility.AngleHelper.getShortestAngleDiff;
import static com.simibubi.create.foundation.utility.AngleHelper.rad;

import com.simibubi.create.AllBlockEntities;
import com.simibubi.create.content.contraptions.base.KineticBlockEntity;
import com.simibubi.create.foundation.gui.widgets.InterpolatedChasingValue;
import com.simibubi.create.foundation.gui.widgets.InterpolatedValue;
import com.simibubi.create.foundation.utility.AnimationTickHolder;
import com.simibubi.create.foundation.utility.CNBTHelper;
import com.simibubi.create.foundation.utility.VecHelper;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

public class CuckooClockBlockEntity extends KineticBlockEntity {

	//public static DamageSource CUCKOO_SURPRISE = new DamageSource("create.cuckoo_clock_explosion").setExplosive();

	public InterpolatedChasingValue hourHand = new InterpolatedChasingValue().withSpeed(.2f);
	public InterpolatedChasingValue minuteHand = new InterpolatedChasingValue().withSpeed(.2f);
	public InterpolatedValue animationProgress = new InterpolatedValue();
	public Animation animationType;
	private boolean sendAnimationUpdate;

	enum Animation {
		PIG, CREEPER, SURPRISE, NONE;
	}

	public CuckooClockBlockEntity() {
		super(AllBlockEntities.CUCKOO_CLOCK);
		animationType = Animation.NONE;
	}
	
	@Override
	protected void fromTag(BlockState state, CompoundTag compound, boolean clientPacket) {
		super.fromTag(state, compound, clientPacket);
		if (clientPacket && compound.contains("Animation")) {
			animationType = CNBTHelper.readEnum(compound, "Animation", Animation.class);
			animationProgress.lastValue = 0;
			animationProgress.value = 0;
		}
	}
	
	@Override
	public void toTag(CompoundTag compound, boolean clientPacket) {
		if (clientPacket && sendAnimationUpdate)
			CNBTHelper.writeEnum(compound, "Animation", animationType);
		sendAnimationUpdate = false;
		super.toTag(compound, clientPacket);
	}

	@Override
	public void tick() {
		super.tick();
		if (getSpeed() == 0)
			return;

		int dayTime = (int) (world.getTimeOfDay() % 24000);
		int hours = (dayTime / 1000 + 6) % 24;
		int minutes = (dayTime % 1000) * 60 / 1000;

		if (!world.isClient) {
			if (animationType == Animation.NONE) {
				if (hours == 12 && minutes < 5)
					startAnimation(Animation.PIG);
				if (hours == 18 && minutes < 36 && minutes > 31)
					startAnimation(Animation.CREEPER);
			} else {
				float value = animationProgress.value;
				animationProgress.set(value + 1);
				if (value > 100)
					animationType = Animation.NONE;

				if (animationType == Animation.SURPRISE && animationProgress.value == 50) {
					Vec3d center = VecHelper.getCenterOf(pos);
					world.breakBlock(pos, false);
					/*world.createExplosion(null, CUCKOO_SURPRISE, null, center.x, center.y, center.z, 3, false,
						Explosion.DestructionType.BREAK);*/
				}

			}
		}

		if (world.isClient) {
			moveHands(hours, minutes);

			if (animationType == Animation.NONE) {
				if (AnimationTickHolder.getTicks() % 32 == 0)
					playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT, 1 / 16f, 2f);
				else if (AnimationTickHolder.getTicks() % 16 == 0)
					playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT, 1 / 16f, 1.5f);
			} else {

				boolean isSurprise = animationType == Animation.SURPRISE;
				float value = animationProgress.value;
				animationProgress.set(value + 1);
				if (value > 100)
					animationType = null;

				// sounds

				if (value == 1)
					playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 2, .5f);
				if (value == 21)
					playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 2, 0.793701f);

				if (value > 30 && isSurprise) {
					Vec3d pos = VecHelper.offsetRandomly(VecHelper.getCenterOf(this.pos), world.random, .5f);
					world.addParticle(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 0, 0, 0);
				}
				if (value == 40 && isSurprise)
					playSound(SoundEvents.ENTITY_TNT_PRIMED, 1f, 1f);

				int step = isSurprise ? 3 : 15;
				for (int phase = 30; phase <= 60; phase += step) {
					if (value == phase - step / 3)
						playSound(SoundEvents.BLOCK_CHEST_OPEN, 1 / 16f, 2f);
					if (value == phase) {
						if (animationType == Animation.PIG)
							playSound(SoundEvents.ENTITY_PIG_AMBIENT, 1 / 4f, 1f);
						else
							playSound(SoundEvents.ENTITY_CREEPER_HURT, 1 / 4f, 3f);
					}
					if (value == phase + step / 3)
						playSound(SoundEvents.BLOCK_CHEST_CLOSE, 1 / 16f, 2f);

				}

			}

			return;
		}
	}

	public void startAnimation(Animation animation) {
		animationType = animation;
		if (animation != null && CuckooClockBlock.containsSurprise(getCachedState()))
			animationType = Animation.SURPRISE;
		animationProgress.lastValue = 0;
		animationProgress.value = 0;
		sendAnimationUpdate = true;
		
		/*if (animation == Animation.CREEPER)
			AllTriggers.triggerForNearbyPlayers(AllTriggers.CUCKOO, world, pos, 10);*/
		
		sendData();
	}

	public void moveHands(int hours, int minutes) {
		float hourTarget = (float) (360 / 12 * (hours % 12));
		float minuteTarget = (float) (360 / 60 * minutes);

		hourHand.target(hourHand.value + rad(getShortestAngleDiff(deg(hourHand.value), hourTarget)));
		minuteHand.target(minuteHand.value + rad(getShortestAngleDiff(deg(minuteHand.value), minuteTarget)));

		hourHand.tick();
		minuteHand.tick();
	}

	private void playSound(SoundEvent sound, float volume, float pitch) {
		Vec3d vec = VecHelper.getCenterOf(pos);
		world.playSound(vec.x, vec.y, vec.z, sound, SoundCategory.BLOCKS, volume, pitch, false);
	}

	@Override
	public boolean shouldRenderAsBE() {
		return true;
	}
}
