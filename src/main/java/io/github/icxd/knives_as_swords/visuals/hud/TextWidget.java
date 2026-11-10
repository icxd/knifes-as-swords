package io.github.icxd.knives_as_swords.visuals.hud;

import io.github.icxd.knives_as_swords.visuals.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A widget that is one short line of text, such as the frames per second. */
public abstract class TextWidget extends HudWidget {
	private final Setting.Color color = color("color", 0xFFFFFF);
	private String line = "";

	protected TextWidget(String id, boolean onAtFirst, float atX, float atY) {
		super(id, onAtFirst, atX, atY);
	}

	/** What to show right now. */
	protected abstract String line(Minecraft client);

	@Override
	protected int width(Minecraft client, boolean editing) {
		line = line(client);
		return client.font.width(line);
	}

	@Override
	protected int height(Minecraft client, boolean editing) {
		return client.font.lineHeight - 1;
	}

	@Override
	protected void draw(GuiGraphicsExtractor graphics, Minecraft client, int width, int height, boolean editing) {
		graphics.text(client.font, line, 0, 0, color.argb(1f));
	}

	/** Frames drawn per second. */
	public static final class Fps extends TextWidget {
		public Fps() {
			super("fps", false, 0.01f, 0.01f);
		}

		@Override
		protected String line(Minecraft client) {
			return client.getFps() + " FPS";
		}
	}

	/** The player's own clicks in the last second, left and right. */
	public static final class Cps extends TextWidget {
		public Cps() {
			super("cps", false, 0.01f, 0.09f);
		}

		@Override
		protected String line(Minecraft client) {
			return ClickCounter.left() + " | " + ClickCounter.right() + " CPS";
		}
	}
}
