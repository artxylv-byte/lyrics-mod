package com.artlyrics.config;

import java.util.Locale;

/** One-click looks: a font, palette, text effect and animation that go well together. */
public enum Look {
	STARLIGHT(ArtlyricsConfig.TextFont.SOFT, LyricStyle.AMETHYST, ArtlyricsConfig.TextEffect.GLOW, ArtlyricsConfig.Animation.FADE),
	PETAL(ArtlyricsConfig.TextFont.SCRIPT, LyricStyle.CHERRY, ArtlyricsConfig.TextEffect.GLOW, ArtlyricsConfig.Animation.RISE),
	NEON(ArtlyricsConfig.TextFont.BOLD, LyricStyle.AURORA, ArtlyricsConfig.TextEffect.GLOW, ArtlyricsConfig.Animation.BLOOM),
	CAMPFIRE(ArtlyricsConfig.TextFont.BUBBLY, LyricStyle.EMBER, ArtlyricsConfig.TextEffect.OUTLINE, ArtlyricsConfig.Animation.RISE),
	NOTEBOOK(ArtlyricsConfig.TextFont.HANDWRITTEN, LyricStyle.QUARTZ, ArtlyricsConfig.TextEffect.SHADOW, ArtlyricsConfig.Animation.TYPEWRITER),
	TIDE(ArtlyricsConfig.TextFont.DECO, LyricStyle.PRISMARINE, ArtlyricsConfig.TextEffect.GLOW, ArtlyricsConfig.Animation.FADE),
	POSTCARD(ArtlyricsConfig.TextFont.TYPEWRITER, LyricStyle.HONEY, ArtlyricsConfig.TextEffect.SHADOW, ArtlyricsConfig.Animation.TYPEWRITER),
	BALLROOM(ArtlyricsConfig.TextFont.FANCY, LyricStyle.HONEY, ArtlyricsConfig.TextEffect.GLOW, ArtlyricsConfig.Animation.FADE),
	BLOCKY(ArtlyricsConfig.TextFont.MINECRAFT, LyricStyle.MOSS, ArtlyricsConfig.TextEffect.SHADOW, ArtlyricsConfig.Animation.BLOOM);

	public final ArtlyricsConfig.TextFont font;
	public final LyricStyle style;
	public final ArtlyricsConfig.TextEffect effect;
	public final ArtlyricsConfig.Animation animation;

	Look(ArtlyricsConfig.TextFont font, LyricStyle style, ArtlyricsConfig.TextEffect effect, ArtlyricsConfig.Animation animation) {
		this.font = font;
		this.style = style;
		this.effect = effect;
		this.animation = animation;
	}

	public String translationKey() {
		return "artlyrics.look." + name().toLowerCase(Locale.ROOT);
	}

	/** True when the config currently matches this look. */
	public boolean matches(ArtlyricsConfig config) {
		return config.font == font && config.style == style && config.textEffect == effect && config.animation == animation;
	}
}
