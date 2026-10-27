package net.povstalec.stellarview.client.screens.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.povstalec.stellarview.client.render.clouds.CloudSettings;
import net.povstalec.stellarview.client.screens.config.ConfigList.BooleanConfigEntry;
import net.povstalec.stellarview.client.screens.config.ConfigList.SliderConfigEntry;
import net.povstalec.stellarview.common.config.CloudsConfig;
import net.povstalec.stellarview.common.config.StellarViewConfigValue;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

public class CloudsConfigScreen extends Screen
{
	private final Screen parentScreen;
	private ConfigList configList;

	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 10;
	private static final int BUTTON_TOP_OFFSET = 26;

	private static final int OPTIONS_LIST_HEADER_HEIGHT = 24;
	private static final int OPTIONS_LIST_BOTTOM_OFFSET = 32;
	private static final int OPTIONS_LIST_ITEM_HEIGHT = 25;

	private static final Component PERCENT = Component.translatable("gui.stellarview.clouds.unit.percent");
	private static final Component BLOCKS = Component.translatable("gui.stellarview.clouds.unit.blocks");


	public CloudsConfigScreen(@Nullable Screen parentScreen)
	{
		super(Component.translatable("gui.stellarview.config.clouds"));
		this.parentScreen = parentScreen;
	}


	@Override
	public void init()
	{
		super.init();

		this.configList = new ConfigList(minecraft, this.width,
				this.height - OPTIONS_LIST_HEADER_HEIGHT - OPTIONS_LIST_BOTTOM_OFFSET, OPTIONS_LIST_HEADER_HEIGHT, OPTIONS_LIST_ITEM_HEIGHT);

		this.configList.add(new CloudBooleanEntry("enabled", this.width, CloudsConfig.enabled));

		this.configList.add(new CloudSliderEntry("distance", PERCENT, this.width, CloudsConfig.distance, CloudSettings::setDistance));
		this.configList.add(new CloudSliderEntry("coverage", PERCENT, this.width, CloudsConfig.coverage, CloudSettings::setCoverage));
		this.configList.add(new CloudSliderEntry("cloud_size", PERCENT, this.width, CloudsConfig.cloud_size, CloudSettings::setCloudSize));
		this.configList.add(new CloudSliderEntry("thickness", BLOCKS, this.width, CloudsConfig.thickness, CloudSettings::setThickness));
		this.configList.add(new CloudSliderEntry("height_offset", BLOCKS, this.width, CloudsConfig.height_offset, CloudSettings::setHeightOffset));
		this.configList.add(new CloudSliderEntry("opacity", PERCENT, this.width, CloudsConfig.opacity, CloudSettings::setOpacity));
		this.configList.add(new CloudSliderEntry("drift_speed", PERCENT, this.width, CloudsConfig.drift_speed, CloudSettings::setDriftSpeed));
		this.configList.add(new CloudSliderEntry("brightness", PERCENT, this.width, CloudsConfig.brightness, CloudSettings::setBrightness));
		this.configList.add(new CloudSliderEntry("sunset_glow", PERCENT, this.width, CloudsConfig.sunset_glow, CloudSettings::setSunsetGlow));

		this.addWidget(this.configList);

		int buttonsLeft = (this.width - 2 * BUTTON_WIDTH - BUTTON_GAP) / 2;

		this.addRenderableWidget(Button.builder(Component.translatable("gui.stellarview.clouds.reset_all"),
						(button) -> resetToDefaults())
				.bounds(buttonsLeft, this.height - BUTTON_TOP_OFFSET, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK,
						(button) ->
						{
							if(this.parentScreen != null)
								this.minecraft.setScreen(this.parentScreen);
							else
								this.onClose();
						})
				.bounds(buttonsLeft + BUTTON_WIDTH + BUTTON_GAP, this.height - BUTTON_TOP_OFFSET, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private void resetToDefaults()
	{
		resetValue(CloudsConfig.enabled);
		resetValue(CloudsConfig.distance);
		resetValue(CloudsConfig.coverage);
		resetValue(CloudsConfig.cloud_size);
		resetValue(CloudsConfig.thickness);
		resetValue(CloudsConfig.height_offset);
		resetValue(CloudsConfig.opacity);
		resetValue(CloudsConfig.drift_speed);
		resetValue(CloudsConfig.brightness);
		resetValue(CloudsConfig.sunset_glow);

		CloudsConfig.apply();
		this.rebuildWidgets(); // The entries have to pick up the new values
	}

	private static void resetValue(StellarViewConfigValue.BooleanValue value)
	{
		if(value.get() != value.getDefault()) // Only save what actually changes
			value.set(value.getDefault());
	}

	private static void resetValue(StellarViewConfigValue.IntValue value)
	{
		if(value.get() != value.getDefault())
			value.set(value.getDefault());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
	{
		super.render(graphics, mouseX, mouseY, partialTick);
		this.configList.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawString(this.font, this.title, (this.width - font.width(this.title)) / 2, 8, 16777215);
	}



	private static void showTooltip(AbstractWidget widget, Tooltip tooltip)
	{
		if(widget.getTooltip() != tooltip) // Setting it every frame would restart the hover delay
			widget.setTooltip(tooltip);
	}



	/**
	 * On/off entry that passes the change on to the cloud renderer
	 */
	private static class CloudBooleanEntry extends BooleanConfigEntry
	{
		private final Tooltip tooltip;

		private CloudBooleanEntry(String name, int screenWidth, StellarViewConfigValue.BooleanValue value)
		{
			super(Component.translatable("gui.stellarview.clouds." + name), screenWidth, value);
			this.tooltip = Tooltip.create(Component.translatable("gui.stellarview.clouds." + name + ".tooltip"));
		}

		@Override
		protected void update()
		{
			CloudsConfig.apply();
		}

		@Override
		public void render(GuiGraphics graphics, int i, int j, int k, int l, int m, int n, int o, boolean bl, float partialTick)
		{
			showTooltip(this.cycleButton, this.tooltip);
			super.render(graphics, i, j, k, l, m, n, o, bl, partialTick);
		}
	}

	/**
	 * Slider entry that shows its unit and passes the change on to the cloud renderer. While the slider is being
	 * dragged the renderer follows it right away, the config file is only written when the value is settled.
	 */
	private static class CloudSliderEntry extends SliderConfigEntry
	{
		private final Tooltip tooltip;
		private final IntConsumer preview;

		private CloudSliderEntry(String name, Component unit, int screenWidth, StellarViewConfigValue.IntValue value, IntConsumer preview)
		{
			super(Component.translatable("gui.stellarview.clouds." + name).append(Component.literal(": ")), unit, screenWidth, value);
			this.tooltip = Tooltip.create(Component.translatable("gui.stellarview.clouds." + name + ".tooltip"));
			this.preview = preview;
		}

		@Override
		protected void update()
		{
			CloudsConfig.apply();
		}

		@Override
		public boolean mouseDragged(double mouseX, double mouseY, int key, double dragX, double dragY)
		{
			boolean dragged = super.mouseDragged(mouseX, mouseY, key, dragX, dragY);

			if(this.sliderButton.isMouseOver(mouseX, mouseY))
				this.preview.accept(this.sliderButton.getValue());

			return dragged;
		}

		@Override
		public void render(GuiGraphics graphics, int i, int j, int k, int l, int m, int n, int o, boolean bl, float partialTick)
		{
			showTooltip(this.sliderButton, this.tooltip);
			super.render(graphics, i, j, k, l, m, n, o, bl, partialTick);
		}
	}
}
