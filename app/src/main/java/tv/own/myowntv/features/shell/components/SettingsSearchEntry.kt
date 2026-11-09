package tv.own.myowntv.features.shell.components

import tv.own.myowntv.ui.components.MyOwnTVIcon

/** One searchable settings row: its group breadcrumb, title, extra keywords, and action. */
internal class SettingsSearchEntry(
    val group: String,
    val title: String,
    keywords: String,
    val icon: MyOwnTVIcon,
    val tone: TileTone,
    val chip: String? = null,
    val chipTone: TileTone = TileTone.PRIMARY,
    val showChevron: Boolean = true,
    val onClick: () -> Unit,
)
