package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.GuiTextStyler
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.ColorCodeUtils
import net.lumalyte.lg.utils.GuildTagValidationMessages
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Guild-tag editor on the shared detail surface. */
class TagEditorMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent, ChatInputHandler {

    private val guildService: GuildService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    private var currentTag: String? = null
    private var inputTag: String? = null
    private var validationError: String? = null
    private var inputInitialized = false

    override fun open() {
        guild = guildService.getGuild(guild.id) ?: guild
        if (!inputInitialized) {
            currentTag = guildService.getTag(guild.id)
            inputTag = currentTag
            inputInitialized = true
        }
        validationError = inputTag?.let(::validateTag)

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(MenuSurface.DETAIL, lang.guiTitle("menu.tag_editor.title", "guild" to guild.name)),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addCurrentTag(pane)
        addInput(pane)
        addStatus(pane)
        addPreview(pane)
        addSave(pane)
        addClear(pane)
        addCancel(pane)
        addFooter(pane)

        gui.show(player)
    }

    private fun addCurrentTag(pane: StaticPane) {
        val item = ItemStack.of(Material.NAME_TAG)
            .name(lang.gui("menu.tag_editor.current.name"))
            .lore(lang.gui("menu.tag_editor.current.guild", "guild" to guild.name))

        currentTag?.let { tag ->
            item.lore(lang.gui("menu.tag_editor.current.tag", "tag" to renderFormattedTag(tag)))
                .lore(lang.gui("menu.tag_editor.current.description"))
        } ?: item.lore(lang.gui("menu.tag_editor.current.not_set"))
            .lore(lang.gui("menu.tag_editor.current.create"))

        pane.addItem(GuiItem(item), 4, 1)
    }

    private fun addInput(pane: StaticPane) {
        val item = ItemStack.of(Material.WRITABLE_BOOK)
            .name(if (isInInputMode()) lang.gui("menu.tag_editor.input.waiting") else lang.gui("menu.tag_editor.input.name"))
            .lore(lang.gui("menu.tag_editor.input.format"))
            .lore(lang.gui("menu.tag_editor.input.gradient"))
            .lore(lang.gui("menu.tag_editor.input.color"))

        inputTag?.let { item.lore(lang.gui("menu.tag_editor.input.current", "tag" to renderFormattedTag(it))) }
            ?: item.lore(lang.gui("menu.tag_editor.input.none"))

        validationError?.let { error ->
            item.lore(lang.gui("menu.tag_editor.input.invalid", "error" to GuiTextStyler.style(Component.text(error))))
        } ?: if (inputTag != null) item.lore(lang.gui("menu.tag_editor.input.valid"))

        item.lore(
            if (isInInputMode()) lang.gui("menu.tag_editor.input.prompt")
            else lang.gui("menu.tag_editor.input.click"),
        )

        pane.addItem(GuiItem(item) {
            if (!isInInputMode()) startChatInput()
            else player.sendMessage(lang.msg("menu.tag_editor.feedback.already_waiting"))
        }, 2, 2)
    }

    private fun addStatus(pane: StaticPane) {
        val count = inputTag?.let(::countVisibleCharacters) ?: 0
        val item = ItemStack.of(if (validationError == null) Material.LIME_DYE else Material.RED_DYE)
            .name(
                when {
                    count > 32 -> lang.gui("menu.tag_editor.status.too_long")
                    count > 28 -> lang.gui("menu.tag_editor.status.nearly_full")
                    else -> lang.gui("menu.tag_editor.status.ok")
                },
            )
            .lore(
                if (count > 32) lang.gui("menu.tag_editor.status.characters_error", "count" to count)
                else lang.gui("menu.tag_editor.status.characters", "count" to count),
            )
        validationError?.let { item.lore(lang.gui("menu.tag_editor.input.invalid", "error" to GuiTextStyler.style(Component.text(it)))) }
        pane.addItem(GuiItem(item), 6, 2)
    }

    private fun addPreview(pane: StaticPane) {
        val preview = inputTag ?: guild.name
        val item = ItemStack.of(Material.ITEM_FRAME)
            .name(lang.gui("menu.tag_editor.preview.name"))
            .lore(lang.gui("menu.tag_editor.preview.description"))

        if (validationError != null) {
            item.lore(lang.gui("menu.tag_editor.preview.invalid_message", "player" to player.name, "tag" to preview))
                .lore(lang.gui("menu.tag_editor.preview.invalid"))
        } else {
            item.lore(lang.gui("menu.tag_editor.preview.message", "player" to player.name, "tag" to renderFormattedTag(preview)))
                .lore(
                    if (inputTag != currentTag) lang.gui("menu.tag_editor.preview.new")
                    else lang.gui("menu.tag_editor.preview.current"),
                )
        }
        pane.addItem(GuiItem(item), 4, 3)
    }

    private fun addSave(pane: StaticPane) {
        val canSave = validationError == null && inputTag != currentTag
        val item = ItemStack.of(if (canSave) Material.LIME_WOOL else Material.GRAY_DYE)
            .name(
                when {
                    validationError != null -> lang.gui("menu.tag_editor.action.save.cannot")
                    inputTag == currentTag -> lang.gui("menu.tag_editor.action.save.no_changes")
                    else -> lang.gui("menu.tag_editor.action.save.name")
                },
            )
            .lore(
                when {
                    validationError != null -> lang.gui("menu.tag_editor.action.save.fix")
                    inputTag == currentTag -> lang.gui("menu.tag_editor.action.save.unchanged")
                    else -> lang.gui("menu.tag_editor.action.save.click")
                },
            )
        pane.addItem(GuiItem(item) { saveTag() }, 2, 4)
    }

    private fun addClear(pane: StaticPane) {
        val item = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.tag_editor.action.clear.name"))
            .lore(lang.gui("menu.tag_editor.action.clear.description"))
            .lore(lang.gui("menu.tag_editor.action.clear.fallback"))
        pane.addItem(GuiItem(item) {
            inputTag = null
            validationError = null
            player.sendMessage(lang.msg("menu.tag_editor.feedback.cleared"))
            open()
        }, 4, 4)
    }

    private fun addCancel(pane: StaticPane) {
        val item = ItemStack.of(Material.RED_WOOL)
            .name(
                if (isInInputMode()) lang.gui("menu.tag_editor.action.cancel.input_name")
                else lang.gui("menu.tag_editor.action.cancel.name"),
            )
            .lore(
                if (isInInputMode()) lang.gui("menu.tag_editor.action.cancel.input_description")
                else lang.gui("menu.tag_editor.action.cancel.description"),
            )
        pane.addItem(GuiItem(item) {
            if (isInInputMode()) {
                chatInputListener.stopInputMode(player)
                player.sendMessage(lang.msg("menu.tag_editor.feedback.input_cancelled"))
                open()
            } else {
                menuNavigator.goBack()
            }
        }, 6, 4)
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW).name(lang.gui("menu.common.item.back.name"))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)

        val home = ItemStack.of(Material.NETHER_STAR).name(lang.gui("menu.control_panel.item.home.name"))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun saveTag() {
        validationError?.let {
            player.sendMessage(lang.msg("menu.tag_editor.feedback.cannot_save", "error" to it))
            return
        }
        if (inputTag == currentTag) {
            player.sendMessage(lang.msg("menu.tag_editor.feedback.no_changes"))
            return
        }

        val tagToSave = inputTag?.let(ColorCodeUtils::convertLegacyToMiniMessage)
        if (!guildService.setTag(guild.id, tagToSave, player.uniqueId)) {
            player.sendMessage(lang.msg("menu.tag_editor.feedback.save_failed"))
            return
        }

        currentTag = tagToSave
        inputTag = tagToSave
        player.sendMessage(lang.msg("menu.tag_editor.feedback.updated"))
        if (tagToSave != null) {
            player.sendMessage(lang.msg("menu.tag_editor.feedback.new_tag", "tag" to ColorCodeUtils.renderTagForDisplay(tagToSave)))
        } else {
            player.sendMessage(lang.msg("menu.tag_editor.feedback.cleared_tag"))
        }
        open()
    }

    private fun startChatInput() {
        chatInputListener.startInputMode(player, this)
        player.closeInventory()
        player.sendMessage(lang.msg("menu.tag_editor.chat.header"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.prompt"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.support"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.legacy"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.colors"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.gradients"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.formatting"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.limit"))
        player.sendMessage(lang.msg("menu.rank_edit.input.cancel"))
        player.sendMessage(lang.msg("menu.tag_editor.chat.footer"))
    }

    private fun validateTag(tag: String): String? {
        val visibleChars = countVisibleCharacters(tag)
        if (visibleChars > 32) return plainLocale("menu.tag_editor.validation.too_long", "count" to visibleChars)
        if (tag.trim().isEmpty()) return plainLocale("menu.tag_editor.validation.empty")
        if (tag.contains("<<") || tag.contains(">>")) return plainLocale("menu.tag_editor.validation.double_brackets")

        net.lumalyte.lg.utils.GuildTagValidator.validationFailure(tag, configService.loadConfig().guild.nameFilter)?.let {
            return GuildTagValidationMessages.legacy(lang, it)
        }

        return try {
            MiniMessage.miniMessage().deserialize(tag)
            null
        } catch (e: Exception) {
            val error = e.message ?: lang.raw("menu.tag_editor.validation.invalid_format")
            when {
                error.contains("unclosed", ignoreCase = true) -> plainLocale("menu.tag_editor.validation.unclosed")
                error.contains("unknown tag", ignoreCase = true) -> plainLocale("menu.tag_editor.validation.unknown_tag")
                error.contains("invalid", ignoreCase = true) -> plainLocale("menu.tag_editor.validation.invalid_syntax")
                else -> plainLocale("menu.tag_editor.validation.format_error", "error" to error.take(50))
            }
        }
    }

    private fun countVisibleCharacters(tag: String): Int = try {
        PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(tag)).length
    } catch (_: Exception) {
        tag.replace(Regex("<[^>]*>"), "")
            .replace(Regex("&[0-9a-fk-or]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\u00A7[0-9a-fk-or]", RegexOption.IGNORE_CASE), "")
            .length
    }

    private fun renderFormattedTag(tag: String): Component =
        runCatching { MiniMessage.miniMessage().deserialize(tag) }.getOrElse { Component.text(tag) }

    private fun plainLocale(key: String, vararg placeholders: Pair<String, Any?>): String =
        PlainTextComponentSerializer.plainText().serialize(lang.msg(key, *placeholders))

    fun setInputTag(tag: String?) {
        inputTag = tag
        validationError = tag?.let(::validateTag)
    }

    fun getInputTag(): String? = inputTag

    fun isInInputMode(): Boolean = chatInputListener.isInInputMode(player)

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
        inputInitialized = false
    }

    override fun onChatInput(player: Player, input: String) {
        val error = validateTag(input)
        if (error != null) {
            player.sendMessage(lang.msg("menu.tag_editor.feedback.invalid", "error" to error))
            return
        }
        setInputTag(input)
        Bukkit.getScheduler().runTask(net.lumalyte.lg.common.PluginKeys.getPlugin(), Runnable { open() })
        player.sendMessage(lang.msg("menu.tag_editor.feedback.set", "tag" to ColorCodeUtils.renderTagForDisplay(input)))
        player.sendMessage(lang.msg("menu.tag_editor.feedback.save_hint"))
    }

    override fun onCancel(player: Player) {
        player.sendMessage(lang.msg("menu.tag_editor.feedback.input_cancelled"))
        Bukkit.getScheduler().runTask(net.lumalyte.lg.common.PluginKeys.getPlugin(), Runnable { open() })
    }
}
