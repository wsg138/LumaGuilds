package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangService

/** Locale keys retained while the old overloaded GuildSettingsMenu is phased out. */
@Suppress("unused")
private fun retainedGuildSettingsLocaleContracts(lang: LangService) {
    lang.gui("menu.guild_settings.item.name.lore.tip")
    lang.gui("menu.guild_settings.item.name.lore.chat")

    lang.gui("menu.guild_settings.item.description.name")
    lang.gui("menu.guild_settings.item.description.lore.set")
    lang.gui("menu.guild_settings.item.description.lore.current", "description" to "Description")
    lang.gui("menu.guild_settings.item.description.lore.not_set")
    lang.gui("menu.guild_settings.item.description.lore.action")
    lang.msg("menu.guild_settings.feedback.no_description_permission")
    lang.msg("menu.guild_settings.feedback.description_requirement")

    lang.gui("menu.guild_settings.item.created.name")
    lang.gui("menu.guild_settings.item.created.lore.date", "date" to "1970-01-01")
    lang.gui("menu.guild_settings.item.created.lore.time", "time" to "00:00:00")

    lang.gui("menu.guild_settings.item.banner.name")
    lang.gui("menu.guild_settings.item.banner.lore.set")
    lang.gui("menu.guild_settings.item.banner.lore.type", "type" to "banner")
    lang.gui("menu.guild_settings.item.banner.lore.error")
    lang.gui("menu.guild_settings.item.banner.lore.action")
    lang.gui("menu.guild_settings.item.banner.lore.not_set")

    lang.gui("menu.guild_settings.item.emoji.name")
    lang.gui("menu.guild_settings.item.emoji.lore.current", "emoji" to "*")
    lang.gui("menu.guild_settings.item.emoji.lore.action")

    lang.gui("menu.guild_settings.item.tag.name")
    lang.gui("menu.guild_settings.item.tag.lore.current", "tag" to "TAG")
    lang.gui("menu.guild_settings.item.tag.lore.action")
    lang.gui("menu.guild_settings.item.tag.lore.formatting")

    lang.gui("menu.guild_settings.item.preview.name")
    lang.gui("menu.guild_settings.item.preview.lore.description")
    lang.gui("menu.guild_settings.item.preview.lore.example", "player" to "Player", "tag" to "TAG")

    lang.gui("menu.guild_settings.item.theme.lore.description")
    lang.gui("menu.guild_settings.item.theme.lore.details")

    lang.gui("menu.guild_settings.item.homes.name")
    lang.gui("menu.guild_settings.item.homes.lore.count", "count" to 0, "available" to 0)
    lang.gui("menu.guild_settings.item.homes.lore.home_main", "home" to "main")
    lang.gui("menu.guild_settings.item.homes.lore.home", "home" to "home")
    lang.gui("menu.guild_settings.item.homes.lore.more", "count" to 0)
    lang.gui("menu.guild_settings.item.homes.lore.manage")
    lang.gui("menu.guild_settings.item.homes.lore.none")
    lang.gui("menu.guild_settings.item.homes.lore.first")
    lang.gui("menu.guild_settings.item.homes.lore.slots", "count" to 0)

    lang.gui("menu.guild_settings.item.access.lore.open")
    lang.gui("menu.guild_settings.item.access.lore.closed")
    lang.msg("menu.guild_settings.feedback.access_open_description")
    lang.msg("menu.guild_settings.feedback.access_closed_description")

    lang.gui("menu.guild_settings.item.tracking.lore.enabled")
    lang.gui("menu.guild_settings.item.tracking.lore.disabled")

    lang.gui("menu.guild_settings.item.members.name")
    lang.gui("menu.guild_settings.item.members.lore.description")
    lang.gui("menu.guild_settings.item.members.lore.details")

    lang.gui("menu.guild_settings.item.mode.lore.peaceful")
    lang.gui("menu.guild_settings.item.mode.lore.hostile")
    lang.gui("menu.guild_settings.item.mode.lore.hostile_cooldown", "days" to 0, "hours" to 0)
    lang.gui("menu.guild_settings.item.mode.lore.peaceful_cooldown", "days" to 0, "hours" to 0)
    lang.gui("menu.guild_settings.item.mode.lore.default_hostile")

    lang.gui("menu.guild_settings.item.back.name")
    lang.gui("menu.guild_settings.item.back.lore")

    lang.gui("menu.guild_settings.item.theme_option.name.current", "theme" to "Theme")
    lang.gui("menu.guild_settings.item.theme_option.name.available", "theme" to "Theme")
    lang.gui("menu.guild_settings.item.theme_option.lore.current")
    lang.gui("menu.guild_settings.item.theme_option.lore.apply")
}
