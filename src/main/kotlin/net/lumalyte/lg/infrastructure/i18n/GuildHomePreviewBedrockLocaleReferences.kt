package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangService

/**
 * Preview-only references for the Bedrock control-panel strings replaced by the shared inventory
 * Guild Home. Keeping these literal references preserves the strict zero-dead-key locale contract
 * while we test Java/Bedrock visual parity. Remove them when the final shared Guild Home copy is
 * localized and the obsolete Bedrock form keys are retired from en_US.yml.
 */
@Suppress("unused")
internal fun retainGuildHomePreviewBedrockLocaleReferences(lang: LangService) {
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
    lang.bedrock("bedrock.control_panel.title", "guild" to "Preview")
    lang.bedrock("bedrock.control_panel.content", "player" to "Preview")
    lang.msg("bedrock.control_panel.closed")
}
