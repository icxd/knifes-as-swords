package io.github.icxd.knives_as_swords.ui;

import io.github.icxd.knives_as_swords.fx.FxMath;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * A button that is on or off, shown by a small switch at its right: a knob that slides across
 * and lights up.
 */
public final class GlowSwitch extends GlowButton {
	private final Component name;
	private final Consumer<Boolean> onChange;
	private boolean on;
	/** Where the knob is, gliding between 0 (off) and 1 (on). */
	private float slid;

	/**
	 * @param onChange called with the new state whenever the player has flipped the switch
	 */
	public GlowSwitch(int x, int y, int width, int height, Component name, boolean on, Consumer<Boolean> onChange) {
		super(x, y, width, height, CommonComponents.optionNameValue(name, on ? CommonComponents.OPTION_ON
				: CommonComponents.OPTION_OFF), () -> { });
		this.name = name;
		this.on = on;
		this.slid = on ? 1f : 0f;
		this.onChange = onChange;
	}

	public boolean on() {
		return on;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		on = !on;
		// What the narrator reads out.
		setMessage(CommonComponents.optionNameValue(name, on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
		onChange.accept(on);
	}

	@Override
	protected void extractLabel(GuiGraphicsExtractor graphics, Font font) {
		slid = Ease.step(slid, on ? 1f : 0f, 18f, Ease.seconds());
		int accent = accent();
		float right = getX() + width - 7f;
		float left = right - 18f;
		float middle = getY() + height / 2f;
		String text = Glow.fit(font, name.getString(), Math.round(left - getX() - 12f));
		graphics.text(font, text, getX() + 7, Math.round(textY(font)), ink(), false);

		if (slid > 0f && active) {
			Glow.halo(graphics, left + 4f, middle - 1f, right - 4f, middle + 1f, 7f, FxMath.fade(accent, 0.55f * slid));
		}
		Glow.rounded(graphics, left, middle - 4.5f, right, middle + 4.5f, 4.5f,
				FxMath.mix(Theme.TRACK, FxMath.mix(accent, 0xFF000000, 0.35f), active ? slid : 0f));
		Glow.disc(graphics, left + 4.5f + 9f * slid, middle, 3.25f,
				active ? FxMath.mix(Theme.DIM, 0xFFFFFFFF, slid) : Theme.FAINT);
	}
}
