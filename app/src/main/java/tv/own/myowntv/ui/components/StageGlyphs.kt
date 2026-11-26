package tv.own.myowntv.ui.components

import tv.own.owntv.core.ui.stage.StageGlyph

/**
 * The Stage line icons, as SVG path data on the same 24×24 grid [MyOwnTVIcon] draws on. The
 * paths live in core ([StageGlyph], shared with the phone); its names are the [MyOwnTVIcon] entries. The
 * phone-only marks (PLUS, MOON, EYE) have no entry here, so the TV keeps drawing ADD and BEDTIME its own
 * way, and has no use for EYE.
 */
internal val StageGlyphPaths: Map<MyOwnTVIcon, String> = StageGlyph.entries.mapNotNull { glyph ->
    MyOwnTVIcon.entries.firstOrNull { it.name == glyph.name }?.let { it to glyph.path }
}.toMap()
