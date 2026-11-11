package tv.own.owntv.core.parser

/**
 * A provider category (id + display name) fed into [tv.own.owntv.core.sync.SyncSupport.refreshCategories].
 *
 * The parser package otherwise died in the RuTube-only redesign (phase 2); this tiny carrier class
 * survives because the no-touch RuTube source files import it under this exact package and name.
 */
data class XtCategory(val id: String, val name: String)
