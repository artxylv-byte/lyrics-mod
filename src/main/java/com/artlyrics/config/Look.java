package com.artlyrics.config;

import java.util.Locale;

/** One-click looks: a font, palette, text effect and animation that go well together. */
public enum Look {
	STARLIGHT(ArtlyricsConfig.TextFont.SOFT, LyricStyle.AMETHYST, ArtlyricsConfig.TextEffect.GLOW, ArtlyricsConfig.Animation.FADE, ArtlyricsConfig.SkyLook.STARLIGHT),
	PETAL(ArtlyricsConfig.TextFont.SCRIPT, LyricStyle.CHERRY, ArtlyricsConfig.TextEffect.GLOW, ArtlyricsConfig.Animation.RISE, ArtlyricsConfig.SkyLook.STARLIGHT),
	NEON(ArtlyricsConfig.TextFont.BOLD, LyricStyle.AURORA, ArtlyricsConfig.TextEffect.GLOW, ArtlyricsConfig.Animation.BLOOM, ArtlyricsConfig.SkyLook.NEON),
	CAMPFIRE(ArtlyricsConfig.TextFont.BUBBLY, LyricStyle.EMBER, ArtlyricsConfig.TextEffect.OUTLINE, ArtlyricsConfig.Animation.RISE, ArtlyricsConfig.SkyLook.CLEAN),
	NOTEBOOK(ArtlyricsConfig.TextFont.HANDWRITTEN, LyricStyle.QUARTZ, ArtlyricsConfig.TextEffect.SHADOW, ArtlyricsConfig.Animation.TYPEWRITER, ArtlyricsConfig.SkyLook.SMOKE),
	TIDE(ArtlyricsConfig.TextFont.DECO, LyricStyle.PRISMARINE, ArtlyricsConfig.TextEffect.GLOW, ArtlyricsConfig.Animation.FADE, ArtlyricsConfig.SkyLook.STARLIGHT),
	POSTCARD(ArtlyricsConfig.TextFont.TYPEWRITER, LyricStyle.HONEY, ArtlyricsConfig.TextEffect.SHADOW, ArtlyricsConfig.Animation.TYPEWRITER, ArtlyricsConfig.SkyLook.CLEAN),
	BALLROOM(ArtlyricsConfig.TextFont.FANCY, LyricStyle.HONEY, ArtlyricsConfig.TextEffect.GLOW, ArtlyricsConfig.Animation.FADE, ArtlyricsConfig.SkyLook.STARLIGHT),
	BLOCKY(ArtlyricsConfig.TextFont.MINECRAFT, LyricStyle.MOSS, ArtlyricsConfig.TextEffect.SHADOW, ArtlyricsConfig.Animation.BLOOM, ArtlyricsConfig.SkyLook.CLEAN),
	/** White pixel capitals that curve gently across the sky, like a daydream above the clouds. */
	DAYDREAM(ArtlyricsConfig.TextFont.PIXEL, LyricStyle.SNOW, ArtlyricsConfig.TextEffect.SHADOW, ArtlyricsConfig.Animation.FADE, ArtlyricsConfig.SkyLook.CLEAN, true, true);

	public final ArtlyricsConfig.TextFont font;
	public final LyricStyle style;
	public final ArtlyricsConfig.TextEffect effect;
	public final ArtlyricsConfig.Animation animation;
	public final ArtlyricsConfig.SkyLook skyLook;
	/** Whether the words are written in capitals. */
	public final boolean uppercase;
	/** Whether the line curves (only looks that need it turn it on). */
	public final boolean arc;

	Look(ArtlyricsConfig.TextFont font, LyricStyle style, ArtlyricsConfig.TextEffect effect, ArtlyricsConfig.Animation animation,
		 ArtlyricsConfig.SkyLook skyLook) {
		this(font, style, effect, animation, skyLook, false, false);
	}

	Look(ArtlyricsConfig.TextFont font, LyricStyle style, ArtlyricsConfig.TextEffect effect, ArtlyricsConfig.Animation animation,
		 ArtlyricsConfig.SkyLook skyLook, boolean uppercase, boolean arc) {
		this.font = font;
		this.style = style;
		this.effect = effect;
		this.animation = animation;
		this.skyLook = skyLook;
		this.uppercase = uppercase;
		this.arc = arc;
	}

	public String translationKey() {
		return "artlyrics.look." + name().toLowerCase(Locale.ROOT);
	}

	/** True when the config currently matches this look. */
	public boolean matches(ArtlyricsConfig config) {
		return config.font == font && config.style == style && config.textEffect == effect && config.animation == animation
				&& config.skyLook == skyLook && config.uppercase == uppercase;
	}
}
