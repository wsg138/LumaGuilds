package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangService

/**
 * Locale-contract references for the dashboard/form entries superseded by the full guild-menu
 * redesign. The locale contract intentionally requires zero unused keys, while these strings are
 * retained temporarily for downgrade compatibility and for servers carrying customized locale
 * files across the redesign. Keeping the exact references here documents that retirement without
 * weakening the dead-key baseline. Remove this file together with the matching YAML blocks once
 * the compatibility window ends.
 */
@Suppress("unused")
private fun retainedGuildMenuLocaleContracts(lang: LangService) {
    // Former Java GuildDashboard.
    lang.gui("menu.dashboard.feedback.not_member")
    lang.gui("menu.dashboard.feedback.guild_missing")
    lang.guiTitle("menu.dashboard.title", "guild" to "Guild")

    lang.gui("menu.dashboard.item.information.name")
    lang.gui("menu.dashboard.item.information.lore.line_1")
    lang.gui("menu.dashboard.item.information.lore.line_2")
    lang.gui("menu.dashboard.item.members.name")
    lang.gui("menu.dashboard.item.members.lore.line_1")
    lang.gui("menu.dashboard.item.members.lore.line_2")
    lang.gui("menu.dashboard.item.ranks.name")
    lang.gui("menu.dashboard.item.ranks.lore.line_1")
    lang.gui("menu.dashboard.item.ranks.lore.line_2")
    lang.gui("menu.dashboard.item.quests.name")
    lang.gui("menu.dashboard.item.quests.lore.line_1")
    lang.gui("menu.dashboard.item.quests.lore.line_2")
    lang.gui("menu.dashboard.item.economy.name")
    lang.gui("menu.dashboard.item.economy.lore.line_1")
    lang.gui("menu.dashboard.item.economy.lore.line_2")
    lang.gui("menu.dashboard.item.settings.name")
    lang.gui("menu.dashboard.item.settings.lore.line_1")
    lang.gui("menu.dashboard.item.settings.lore.line_2")
    lang.gui("menu.dashboard.item.progression.name")
    lang.gui("menu.dashboard.item.progression.lore.line_1")
    lang.gui("menu.dashboard.item.progression.lore.line_2")
    lang.gui("menu.dashboard.item.diplomacy.name")
    lang.gui("menu.dashboard.item.diplomacy.lore.line_1")
    lang.gui("menu.dashboard.item.diplomacy.lore.line_2")
    lang.gui("menu.dashboard.item.warfare.name")
    lang.gui("menu.dashboard.item.warfare.lore.line_1")
    lang.gui("menu.dashboard.item.warfare.lore.line_2")
    lang.gui("menu.dashboard.item.statistics.name")
    lang.gui("menu.dashboard.item.statistics.lore.line_1")
    lang.gui("menu.dashboard.item.statistics.lore.line_2")

    lang.gui("menu.dashboard.item.guild_info.name", "display_name" to "Guild")
    lang.gui("menu.dashboard.item.guild_info.lore.members", "member_count" to 0)
    lang.gui("menu.dashboard.item.guild_info.lore.ranks", "rank_count" to 0)
    lang.gui("menu.dashboard.item.guild_info.lore.balance", "balance" to 0)
    lang.gui("menu.dashboard.item.guild_info.lore.prompt_line_1")
    lang.gui("menu.dashboard.item.guild_info.lore.prompt_line_2")

    // Former BedrockGuildControlPanelMenu SimpleForm.
    lang.bedrock("bedrock.control_panel.title", "guild" to "Guild")
    lang.bedrock("bedrock.control_panel.content", "player" to "Player")
    lang.bedrock("bedrock.control_panel.button.members")
    lang.bedrock("bedrock.control_panel.button.settings")
    lang.bedrock("bedrock.control_panel.button.bank")
    lang.bedrock("bedrock.control_panel.button.ranks")
    lang.bedrock("bedrock.control_panel.button.tag")
    lang.bedrock("bedrock.control_panel.button.statistics")
    lang.bedrock("bedrock.control_panel.button.information")
    lang.bedrock("bedrock.control_panel.button.mode")
    lang.bedrock("bedrock.control_panel.button.rank_list")
    lang.bedrock("bedrock.control_panel.button.invite")
    lang.bedrock("bedrock.control_panel.button.kick")
    lang.bedrock("bedrock.control_panel.button.change_rank")
    lang.bedrock("bedrock.control_panel.button.progression")
    lang.bedrock("bedrock.control_panel.button.homes")
    lang.bedrock("bedrock.control_panel.button.emoji")
    lang.bedrock("bedrock.control_panel.button.wars")
    lang.bedrock("bedrock.control_panel.button.relations")
    lang.bedrock("bedrock.control_panel.button.close")
    lang.msg("bedrock.control_panel.closed")
}
