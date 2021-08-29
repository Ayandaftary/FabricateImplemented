package com.simibubi.create.content.curiosities.tools;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.utility.AnimationTickHolder;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.DynamicItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms.TransformType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class SandPaperItemRenderer implements DynamicItemRenderer {

	@Override
	public void render(ItemStack stack, TransformType transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
		LocalPlayer player = Minecraft.getInstance().player;
		SandPaperModel mainModel = (SandPaperModel) itemRenderer.getModel(stack, Minecraft.getInstance().level, null, 0);
		float partialTicks = AnimationTickHolder.getPartialTicks();

		boolean leftHand = transformType == TransformType.FIRST_PERSON_LEFT_HAND;
		boolean firstPerson = leftHand || transformType == TransformType.FIRST_PERSON_RIGHT_HAND;

		ms.pushPose();
		ms.translate(.5f, .5f, .5f);

		CompoundTag tag = stack.getOrCreateTag();
		boolean jeiMode = tag.contains("JEI");

		if (tag.contains("Polishing")) {
			ms.pushPose();

			if (transformType == TransformType.GUI) {
				ms.translate(0.0F, .2f, 1.0F);
				ms.scale(.75f, .75f, .75f);
			} else {
				int modifier = leftHand ? -1 : 1;
				ms.mulPose(Vector3f.YP.rotationDegrees(modifier * 40));
			}

			// Reverse bobbing
			float time = (float) (!jeiMode ? player.getUseItemRemainingTicks()
					: (-AnimationTickHolder.getTicks()) % stack.getUseDuration()) - partialTicks + 1.0F;
			if (time / (float) stack.getUseDuration() < 0.8F) {
				float bobbing = -Mth.abs(Mth.cos(time / 4.0F * (float) Math.PI) * 0.1F);

				if (transformType == TransformType.GUI)
					ms.translate(bobbing, bobbing, 0.0F);
				else
					ms.translate(0.0f, bobbing, 0.0F);
			}

			ItemStack toPolish = ItemStack.of(tag.getCompound("Polishing"));
			itemRenderer.renderStatic(toPolish, TransformType.NONE, light, overlay, ms, buffer);

			ms.popPose();
		}

		if (firstPerson) {
			int itemInUseCount = player.getUseItemRemainingTicks();
			if (itemInUseCount > 0) {
				int modifier = leftHand ? -1 : 1;
				ms.translate(modifier * .5f, 0, -.25f);
				ms.mulPose(Vector3f.ZP.rotationDegrees(modifier * 40));
				ms.mulPose(Vector3f.XP.rotationDegrees(modifier * 10));
				ms.mulPose(Vector3f.YP.rotationDegrees(modifier * 90));
			}
		}

		itemRenderer.render(stack, TransformType.NONE, false, ms, buffer, light, overlay, mainModel.getOriginalModel());

		ms.popPose();
	}

	public static class SandPaperModel extends CustomRenderedItemModel {

		public SandPaperModel(BakedModel template) {
			super(template, "");
		}

		@Override
		public DynamicItemRenderer createRenderer() {
			return new SandPaperItemRenderer();
		}

	}

}
