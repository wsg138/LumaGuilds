package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangService

/**
 * Focused-preview compatibility reference for the existing localized dashboard title.
 *
 * The preview currently draws its own visual header, but the production locale contract must keep
 * the dashboard title translation alive until the final Guild Home copy is localized.
 */
@Suppress("unused")
internal fun retainGuildHomePreviewTitleLocale(lang: LangService) {
    lang.gui("menu.dashboard.title", "guild" to "Preview")
}
