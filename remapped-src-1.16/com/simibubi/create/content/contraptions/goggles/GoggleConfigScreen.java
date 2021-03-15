package com.simibubi.create.content.contraptions.goggles;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.config.AllConfigs;
import com.simibubi.create.foundation.gui.AbstractSimiScreen;
import com.simibubi.create.foundation.gui.GuiGameElement;
import com.simibubi.create.foundation.utility.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class GoggleConfigScreen extends AbstractSimiScreen {

	private int offsetX;
	private int offsetY;
	private final List<Text> tooltip;

	public GoggleConfigScreen() {
		Text componentSpacing = new LiteralText("    ");
		tooltip = new ArrayList<>();
		tooltip.add(componentSpacing.copy().append(Lang.translate("gui.config.overlay1")));
		tooltip.add(componentSpacing.copy().append(Lang.translate("gui.config.overlay2").formatted(Formatting.GRAY)));
		tooltip.add(LiteralText.EMPTY);
		tooltip.add(componentSpacing.copy().append(Lang.translate("gui.config.overlay3")));
		tooltip.add(componentSpacing.copy().append(Lang.translate("gui.config.overlay4")));
		tooltip.add(LiteralText.EMPTY);
		tooltip.add(componentSpacing.copy().append(Lang.translate("gui.config.overlay5").formatted(Formatting.GRAY)));
		tooltip.add(componentSpacing.copy().append(Lang.translate("gui.config.overlay6").formatted(Formatting.GRAY)));
		tooltip.add(LiteralText.EMPTY);
		tooltip.add(componentSpacing.copy().append(Lang.translate("gui.config.overlay7")));
		tooltip.add(componentSpacing.copy().append(Lang.translate("gui.config.overlay8")));
	}

	@Override
	protected void init() {
		MinecraftClient mc = MinecraftClient.getInstance();
		this.width = mc.getWindow().getScaledWidth();
		this.height = mc.getWindow().getScaledHeight();

		offsetX = AllConfigs.CLIENT.overlayOffsetX.get();
		offsetY = AllConfigs.CLIENT.overlayOffsetY.get();
	}

	@Override
	public void removed() {
		AllConfigs.CLIENT.overlayOffsetX.set(offsetX);
		AllConfigs.CLIENT.overlayOffsetY.set(offsetY);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		updateOffset(x, y);

		return true;
	}

	@Override
	public boolean mouseDragged(double p_mouseDragged_1_, double p_mouseDragged_3_, int p_mouseDragged_5_, double p_mouseDragged_6_, double p_mouseDragged_8_) {
		updateOffset(p_mouseDragged_1_, p_mouseDragged_3_);

		return true;
	}

	private void updateOffset(double windowX, double windowY) {
		offsetX = (int) (windowX - (this.width / 2));
		offsetY = (int) (windowY - (this.height / 2));
	}

	@Override
	protected void renderWindow(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
		ms.push();
		int posX = this.width / 2 + offsetX;
		int posY = this.height / 2 + offsetY;
		renderTooltip(ms, tooltip, posX, posY);

		ItemStack item = AllItems.GOGGLES.asStack();
		GuiGameElement.of(item).atLocal(posX + 10, posY, 450).render(ms);
		ms.pop();
	}
}
