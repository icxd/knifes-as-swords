package io.github.icxd.knives_as_swords.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.icxd.knives_as_swords.fx.FxMath;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A button that steps through a list of choices: a click goes to the next one, a right-click or
 * a click with Shift held to the one before.
 *
 * @param <T> what there is to choose from
 */
public final class GlowCycle<T> extends GlowButton {
	private final @Nullable Component name;
	private final List<T> values;
	private final Function<T, Component> label;
	private final Consumer<T> onChange;
	private int index;

	/**
	 * @param name what is being chosen, written in front of the choice; null to show the choice alone
	 * @param label what to show for a choice
	 * @param onChange called with the new choice whenever the player has made one
	 */
	public GlowCycle(int x, int y, int width, int height, @Nullable Component name, List<T> values, T initial,
			Function<T, Component> label, Consumer<T> onChange) {
		super(x, y, width, height, CommonComponents.EMPTY, () -> { });
		this.name = name;
		this.values = List.copyOf(values);
		this.label = label;
		this.onChange = onChange;
		this.index = Math.max(this.values.indexOf(initial), 0);
		setMessage(whole());
	}

	public T value() {
		return values.get(index);
	}

	private Component whole() {
		Component choice = label.apply(value());
		return name != null ? CommonComponents.optionNameValue(name, choice) : choice;
	}

	@Override
	protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
		return buttonInfo.button() == InputConstants.MOUSE_BUTTON_LEFT
				|| buttonInfo.button() == InputConstants.MOUSE_BUTTON_RIGHT;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		boolean back = input.hasShiftDown()
				|| (input instanceof MouseButtonEvent event && event.button() == InputConstants.MOUSE_BUTTON_RIGHT);
		index = Math.floorMod(index + (back ? -1 : 1), values.size());
		setMessage(whole());
		onChange.accept(value());
	}

	@Override
	protected void extractLabel(GuiGraphicsExtractor graphics, Font font) {
		if (name == null) {
			super.extractLabel(graphics, font);
			return;
		}
		// What is chosen stands out from what it is a choice of, and is what stays where both do not fit.
		String front = name.getString() + ": ";
		String whole = label.apply(value()).getString();
		if (font.width(front) + font.width(whole) > width - 4) {
			front = "";
		}
		String choice = Glow.fit(font, whole, width - 4 - font.width(front));
		float x = getX() + (width - font.width(front) - font.width(choice)) / 2f;
		graphics.text(font, front, Math.round(x), Math.round(textY(font)), ink(), false);
		Glow.text(graphics, font, choice, Math.round(x) + font.width(front), textY(font),
				active ? FxMath.mix(accent(), 0xFFFFFFFF, 0.45f + 0.3f * hover()) : Theme.FAINT,
				FxMath.fade(accent(), active ? 0.2f + 0.3f * hover() : 0f));
	}
}
