package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Guild emoji editor on the shared redesign detail surface. */
class GuildEmojiMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val nexoEmojiService: NexoEmojiService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    private var loaded = false
    private var currentEmoji: String? = null
    private var inputEmoji: String? = null
    private var validationError: String? = null

    override fun open() {
        guild = guildService.getGuild(guild.id) ?: guild
        if (!loaded) {
            currentEmoji = guildService.getEmoji(guild.id)
            inputEmoji = currentEmoji
            loaded = true
        }
        validateCurrentInput()

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(MenuSurface.DETAIL, lang.guiTitle("menu.guild_emoji.title", "guild" to guild.name)),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addCurrentEmoji(pane)
        addNexoStatus(pane)
        addInput(pane)
        addSelector(pane)
        addPreview(pane)
        addSave(pane)
        addClear(pane)
        addCancel(pane)
        addFooter(pane)

        gui.show(player)
    }

    private fun addCurrentEmoji(pane: StaticPane) {
        val item = ItemStack.of(Material.NAME_TAG)
            .name(lang.gui("menu.guild_emoji.current.name"))
            .lore(lang.gui("menu.guild_emoji.current.value", "emoji" to (currentEmoji ?: lang.raw("menu.guild_emoji.current.not_set"))))
            .lore(lang.gui("menu.guild_emoji.current.description"))
        pane.addItem(GuiItem(item), 4, 1)
    }

    private fun addNexoStatus(pane: StaticPane) {
        val available = nexoEmojiService.isNexoAvailable()
        val item = if (available) {
            ItemStack.of(Material.LIME_WOOL)
                .name(lang.gui("menu.guild_emoji.nexo.available.name"))
                .lore(lang.gui("menu.guild_emoji.nexo.available.detected"))
                .lore(lang.gui("menu.guild_emoji.nexo.available.validation"))
        } else {
            ItemStack.of(Material.RED_WOOL)
                .name(lang.gui("menu.guild_emoji.nexo.unavailable.name"))
                .lore(lang.gui("menu.guild_emoji.nexo.unavailable.missing"))
                .lore(lang.gui("menu.guild_emoji.nexo.unavailable.contact"))
        }
        pane.addItem(GuiItem(item) {
            player.sendMessage(lang.msg("menu.guild_emoji.nexo.status", "status" to nexoEmojiService.getNexoStatusDescription()))
        }, 7, 1)
    }

    private fun addInput(pane: StaticPane) {
        val item = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.gui("menu.guild_emoji.input.name"))
            .lore(lang.gui("menu.guild_emoji.input.format"))
            .lore(lang.gui("menu.guild_emoji.input.example"))
            .lore(lang.gui("menu.guild_emoji.input.current", "emoji" to (inputEmoji ?: lang.raw("menu.guild_emoji.input.none"))))

        val error = validationError
        if (error != null) {
            item.lore(lang.gui("menu.guild_emoji.input.invalid", "error" to error))
        } else if (inputEmoji != null) {
            item.lore(lang.gui("menu.guild_emoji.input.valid"))
        }
        item.lore(lang.gui("menu.guild_emoji.input.click"))

        pane.addItem(GuiItem(item) {
            chatInputListener.startInputMode(player, EmojiInputHandler(this, lang))
            player.closeInventory()
            player.sendMessage(lang.msg("menu.guild_emoji.chat.prompt"))
            player.sendMessage(lang.msg("menu.guild_emoji.chat.format"))
            player.sendMessage(lang.msg("menu.guild_emoji.chat.cancel"))
        }, 1, 2)
    }

    private fun addSelector(pane: StaticPane) {
        val unlocked = nexoEmojiService.getPlayerUnlockedEmojis(player).size
        val item = ItemStack.of(if (unlocked > 0) Material.ENDER_CHEST else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_emoji.selector.name"))
            .lore(lang.gui("menu.guild_emoji.selector.description"))
            .lore(lang.gui("menu.guild_emoji.selector.count", "count" to unlocked))
            .lore(lang.gui("menu.guild_emoji.selector.click"))
        pane.addItem(GuiItem(item) {
            if (unlocked == 0) {
                player.sendMessage(lang.msg("menu.guild_emoji.feedback.none_unlocked"))
                player.sendMessage(lang.msg("menu.guild_emoji.feedback.contact_admin"))
            } else {
                menuNavigator.openMenu(menuFactory.createEmojiSelectionMenu(menuNavigator, player, guild, this))
            }
        }, 4, 2)
    }

    private fun addPreview(pane: StaticPane) {
        val previewEmoji = inputEmoji ?: ":cat:"
        val item = ItemStack.of(Material.ITEM_FRAME)
            .name(lang.gui("menu.guild_emoji.preview.name"))
            .lore(lang.gui("menu.guild_emoji.preview.description"))
        if (validationError != null) {
            item.lore(lang.gui("menu.guild_emoji.preview.invalid_message", "player" to player.name, "emoji" to previewEmoji))
                .lore(lang.gui("menu.guild_emoji.preview.invalid"))
        } else {
            item.lore(lang.gui("menu.guild_emoji.preview.message", "player" to player.name, "emoji" to previewEmoji))
                .lore(lang.gui("menu.guild_emoji.preview.valid"))
        }
        pane.addItem(GuiItem(item), 7, 2)
    }

    private fun addSave(pane: StaticPane) {
        val canSave = validationError == null && inputEmoji != currentEmoji
        val item = ItemStack.of(if (canSave) Material.LIME_WOOL else Material.GRAY_DYE)
            .name(
                when {
                    validationError != null -> lang.gui("menu.guild_emoji.action.save.cannot")
                    inputEmoji == currentEmoji -> lang.gui("menu.guild_emoji.action.save.no_changes")
                    else -> lang.gui("menu.guild_emoji.action.save.name")
                },
            )
            .lore(
                when {
                    validationError != null -> lang.gui("menu.guild_emoji.action.save.fix")
                    inputEmoji == currentEmoji -> lang.gui("menu.guild_emoji.action.save.unchanged")
                    else -> lang.gui("menu.guild_emoji.action.save.click")
                },
            )
        pane.addItem(GuiItem(item) { saveEmoji() }, 2, 4)
    }

    private fun addClear(pane: StaticPane) {
        val item = ItemStack.of(Material.ORANGE_WOOL)
            .name(lang.gui("menu.guild_emoji.action.clear.name"))
            .lore(lang.gui("menu.guild_emoji.action.clear.description"))
            .lore(lang.gui("menu.guild_emoji.action.clear.fallback"))
        pane.addItem(GuiItem(item) {
            if (guildService.setEmoji(guild.id, null, player.uniqueId)) {
                currentEmoji = null
                inputEmoji = null
                validationError = null
                player.sendMessage(lang.msg("menu.guild_emoji.feedback.cleared"))
                open()
            } else {
                player.sendMessage(lang.msg("menu.guild_emoji.feedback.save_failed"))
            }
        }, 4, 4)
    }

    private fun addCancel(pane: StaticPane) {
        val item = ItemStack.of(Material.RED_WOOL)
            .name(lang.gui("menu.tag_editor.action.cancel.name"))
            .lore(lang.gui("menu.tag_editor.action.cancel.description"))
        pane.addItem(GuiItem(item) { menuNavigator.goBack() }, 6, 4)
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_emoji.action.back.name"))
            .lore(lang.gui("menu.guild_emoji.action.back.description"))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)

        val home = ItemStack.of(Material.NETHER_STAR).name(lang.gui("menu.control_panel.item.home.name"))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun saveEmoji() {
        validationError?.let {
            player.sendMessage(lang.msg("menu.guild_emoji.feedback.cannot_save", "error" to it))
            return
        }
        if (inputEmoji == currentEmoji) {
            player.sendMessage(lang.msg("menu.guild_emoji.feedback.no_changes"))
            return
        }
        if (!guildService.setEmoji(guild.id, inputEmoji, player.uniqueId)) {
            player.sendMessage(lang.msg("menu.guild_emoji.feedback.save_failed"))
            return
        }
        currentEmoji = inputEmoji
        player.sendMessage(
            lang.msg(
                "menu.guild_emoji.feedback.new_emoji",
                "emoji" to (inputEmoji ?: lang.gui("menu.guild_emoji.feedback.cleared_value")),
            ),
        )
        player.sendMessage(lang.msg("menu.guild_emoji.feedback.updated"))
        open()
    }

    private fun validateCurrentInput() {
        val input = inputEmoji
        validationError = when {
            input.isNullOrBlank() -> null
            validateEmojiFormat(input) is ValidationResult.Invalid ->
                (validateEmojiFormat(input) as ValidationResult.Invalid).message
            !nexoEmojiService.doesEmojiExist(input) -> lang.raw("menu.guild_emoji.validation.not_found")
            else -> null
        }
    }

    private fun validateEmojiFormat(emoji: String?): ValidationResult {
        if (emoji.isNullOrBlank()) return ValidationResult.Valid
        if (!emoji.startsWith(":") || !emoji.endsWith(":")) {
            return ValidationResult.Invalid(lang.raw("menu.guild_emoji.validation.format"))
        }
        val name = emoji.substring(1, emoji.length - 1)
        if (name.isBlank()) return ValidationResult.Invalid(lang.raw("menu.guild_emoji.validation.empty"))
        if (emoji.length > 50) return ValidationResult.Invalid(lang.raw("menu.guild_emoji.validation.too_long"))
        val forbidden = setOf('<', '>', '"', '\'', '\\', '\n', '\r', '\t')
        if (name.any { it in forbidden }) return ValidationResult.Invalid(lang.raw("menu.guild_emoji.validation.characters"))
        return ValidationResult.Valid
    }

    private fun setInputEmoji(emoji: String?) {
        inputEmoji = emoji
        validateCurrentInput()
    }

    fun setEmojiInput(emoji: String?): Boolean {
        setInputEmoji(emoji)
        return validationError == null
    }

    fun getValidationError(): String? = validationError

    fun isInputValid(): Boolean = validationError == null

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
        loaded = false
    }
}

private class EmojiInputHandler(
    private val emojiMenu: GuildEmojiMenu,
    private val lang: LangService,
) : ChatInputHandler {
    override fun onChatInput(player: Player, input: String) {
        if (input.equals("cancel", ignoreCase = true)) {
            onCancel(player)
            return
        }
        if (!input.startsWith(":") || !input.endsWith(":")) {
            player.sendMessage(lang.msg("menu.guild_emoji.feedback.invalid_format"))
            player.sendMessage(lang.msg("menu.guild_emoji.input.example"))
            return
        }
        emojiMenu.setEmojiInput(input)
        val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable { emojiMenu.open() })
        player.sendMessage(lang.msg("menu.guild_emoji.feedback.set", "emoji" to input))
        player.sendMessage(lang.msg("menu.guild_emoji.feedback.save_hint"))
    }

    override fun onCancel(player: Player) {
        player.sendMessage(lang.msg("menu.guild_emoji.feedback.input_cancelled"))
        val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable { emojiMenu.open() })
    }
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}

/** Dense browser for the player's unlocked emoji collection. */
class EmojiSelectionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val parentMenu: GuildEmojiMenu,
) : Menu, KoinComponent {

    private val nexoEmojiService: NexoEmojiService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    private var currentPage = 0
    private val itemsPerPage = 45

    override fun open() {
        val unlocked = nexoEmojiService.getPlayerUnlockedEmojis(player)
        if (unlocked.isEmpty()) {
            player.sendMessage(lang.msg("menu.guild_emoji.feedback.none_unlocked"))
            menuNavigator.openMenu(parentMenu)
            return
        }

        val totalPages = maxOf(1, (unlocked.size + itemsPerPage - 1) / itemsPerPage)
        currentPage = currentPage.coerceIn(0, totalPages - 1)
        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.LIST,
                lang.guiTitle("menu.guild_emoji.selection.title", "page" to currentPage + 1, "total" to totalPages),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        unlocked.drop(currentPage * itemsPerPage).take(itemsPerPage).forEachIndexed { index, emojiName ->
            val placeholder = ":$emojiName:"
            val item = NexoItemProvider.getItemStackOrFallback("lg_emoji_choice_$emojiName") { ItemStack.of(Material.PAPER) }
                .name(lang.gui("menu.guild_emoji.selection.emoji", "emoji" to placeholder))
                .lore(lang.gui("menu.guild_emoji.selection.select"))
                .lore(lang.gui("menu.guild_emoji.selection.description"))
            pane.addItem(GuiItem(item) {
                parentMenu.setEmojiInput(placeholder)
                menuNavigator.openMenu(parentMenu)
                player.sendMessage(lang.msg("menu.guild_emoji.feedback.selected", "emoji" to placeholder))
            }, index % 9, index / 9)
        }

        if (currentPage > 0) {
            val previous = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_emoji.selection.previous.name"))
                .lore(lang.gui("menu.guild_emoji.selection.previous.description"))
            pane.addItem(GuiItem(previous) { currentPage--; open() }, 0, 5)
        }

        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_emoji.selection.back.name"))
            .lore(lang.gui("menu.guild_emoji.selection.back.description"))
        pane.addItem(GuiItem(back) { menuNavigator.openMenu(parentMenu) }, 2, 5)

        val home = ItemStack.of(Material.NETHER_STAR).name(lang.gui("menu.control_panel.item.home.name"))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 6, 5)

        if (currentPage < totalPages - 1) {
            val next = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_emoji.selection.next.name"))
                .lore(lang.gui("menu.guild_emoji.selection.next.description"))
            pane.addItem(GuiItem(next) { currentPage++; open() }, 8, 5)
        }

        gui.show(player)
    }
}
