package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Focused description editor using the shared redesign detail surface. */
class DescriptionEditorMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent, ChatInputHandler {

    private val guildService: GuildService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    private var loaded = false
    private var currentDescription: String? = null
    private var inputDescription: String? = null
    private var validationError: Component? = null

    override fun open() {
        guild = guildService.getGuild(guild.id) ?: guild
        if (!loaded) {
            currentDescription = guildService.getDescription(guild.id)
            inputDescription = currentDescription
            loaded = true
        }
        validationError = validateDescription(inputDescription)

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(MenuSurface.DETAIL, lang.guiTitle("menu.description_editor.title")),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addCurrentDescription(pane)
        addInput(pane)
        addValidation(pane)
        addPreview(pane)
        addSave(pane)
        addCancel(pane)
        addFooter(pane)

        gui.show(player)
    }

    private fun addCurrentDescription(pane: StaticPane) {
        val item = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.description_editor.current.name"))
            .lore(
                lang.gui(
                    "menu.description_editor.current.value",
                    "description" to (parseMiniMessageForDisplay(currentDescription)
                        ?: lang.gui("menu.description_editor.current.none")),
                ),
            )
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.description_editor.current.scope"))
        pane.addItem(GuiItem(item), 4, 1)
    }

    private fun addInput(pane: StaticPane) {
        val canEdit = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_DESCRIPTION)
        val item = ItemStack.of(if (canEdit) Material.WRITABLE_BOOK else Material.GRAY_DYE)
            .name(lang.gui("menu.description_editor.input.name"))
            .lore(lang.gui("menu.description_editor.input.description"))
            .lore(lang.gui("menu.description_editor.input.value", "description" to (inputDescription ?: lang.raw("menu.description_editor.input.none"))))
            .lore(lang.gui("menu.description_editor.input.limit"))
        pane.addItem(GuiItem(item) {
            if (canEdit) startChatInput()
            else player.sendMessage(lang.msg("menu.description_editor.feedback.no_permission"))
        }, 2, 2)
    }

    private fun addValidation(pane: StaticPane) {
        val error = validationError
        val item = if (error != null) {
            ItemStack.of(Material.RED_CONCRETE)
                .name(lang.gui("menu.description_editor.validation.error.name"))
                .lore(lang.gui("menu.description_editor.validation.error.description", "error" to error))
        } else {
            ItemStack.of(Material.GREEN_CONCRETE)
                .name(lang.gui("menu.description_editor.validation.valid.name"))
                .lore(lang.gui("menu.description_editor.validation.valid.description"))
        }
        pane.addItem(GuiItem(item), 6, 2)
    }

    private fun addPreview(pane: StaticPane) {
        val item = ItemStack.of(Material.ITEM_FRAME)
            .name(lang.gui("menu.description_editor.preview.name"))
            .lore(lang.gui("menu.description_editor.preview.description"))

        val input = inputDescription
        when {
            input == null -> item.lore(lang.gui("menu.description_editor.preview.empty"))
            validationError != null -> item.lore(lang.gui("menu.description_editor.preview.error"))
            else -> {
                val plain = runCatching {
                    PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(input))
                }.getOrNull()
                if (plain != null) item.lore(lang.gui("menu.description_editor.preview.value", "description" to plain))
                else item.lore(lang.gui("menu.description_editor.preview.error"))
            }
        }
        pane.addItem(GuiItem(item), 4, 3)
    }

    private fun addSave(pane: StaticPane) {
        val canEdit = guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_DESCRIPTION)
        val canSave = canEdit && validationError == null && inputDescription != currentDescription
        val item = ItemStack.of(if (canSave) Material.EMERALD_BLOCK else Material.GRAY_DYE)
            .name(
                if (canSave) lang.gui("menu.description_editor.save.name")
                else lang.gui("menu.description_editor.save.disabled"),
            )
            .lore(
                if (canSave) lang.gui("menu.description_editor.save.effect")
                else lang.gui("menu.description_editor.save.invalid"),
            )
        pane.addItem(GuiItem(item) {
            when {
                !canEdit -> player.sendMessage(lang.msg("menu.description_editor.feedback.no_permission"))
                canSave -> saveDescription()
                else -> player.sendMessage(lang.msg("menu.description_editor.feedback.cannot_save"))
            }
        }, 3, 4)
    }

    private fun addCancel(pane: StaticPane) {
        val item = ItemStack.of(Material.REDSTONE_BLOCK)
            .name(lang.gui("menu.description_editor.cancel.name"))
            .lore(lang.gui("menu.description_editor.cancel.description"))
        pane.addItem(GuiItem(item) { menuNavigator.goBack() }, 5, 4)
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW).name(lang.gui("menu.common.item.back.name"))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)

        val home = ItemStack.of(Material.NETHER_STAR).name(lang.gui("menu.common.item.home.name"))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun saveDescription() {
        if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_DESCRIPTION)) {
            player.sendMessage(lang.msg("menu.description_editor.feedback.no_permission"))
            return
        }
        if (!guildService.setDescription(guild.id, inputDescription, player.uniqueId)) {
            player.sendMessage(lang.msg("menu.description_editor.feedback.update_failed"))
            return
        }

        currentDescription = inputDescription
        guild = guildService.getGuild(guild.id) ?: guild
        player.sendMessage(lang.msg("menu.description_editor.feedback.updated"))
        currentDescription?.let { player.sendMessage(lang.msg("menu.description_editor.feedback.new_description", "description" to it)) }
            ?: player.sendMessage(lang.msg("menu.description_editor.feedback.cleared"))
        menuNavigator.goBack()
    }

    private fun validateDescription(description: String?): Component? {
        if (description == null) return null
        if (description.length > 100) {
            return lang.gui("menu.description_editor.validation.too_long", "length" to description.length)
        }
        return runCatching { MiniMessage.miniMessage().deserialize(description) }
            .exceptionOrNull()
            ?.let { lang.gui("menu.description_editor.validation.invalid_format", "error" to (it.message ?: lang.raw("menu.description_editor.validation.unknown_error"))) }
    }

    private fun startChatInput() {
        player.sendMessage(lang.msg("menu.description_editor.chat.header"))
        player.sendMessage(lang.msg("menu.description_editor.chat.prompt"))
        player.sendMessage(lang.msg("menu.description_editor.chat.formatting"))
        player.sendMessage(lang.msg("menu.description_editor.chat.limit"))
        player.sendMessage(lang.msg("menu.description_editor.chat.cancel"))
        chatInputListener.startInputMode(player, this)
        player.closeInventory()
    }

    override fun onChatInput(player: Player, input: String) {
        if (input.equals("cancel", ignoreCase = true)) {
            player.sendMessage(lang.msg("menu.description_editor.feedback.cancelled"))
            open()
            return
        }
        inputDescription = input
        validationError = validateDescription(input)
        open()
    }

    override fun onCancel(player: Player) {
        player.sendMessage(lang.msg("menu.description_editor.feedback.cancelled"))
        open()
    }

    private fun parseMiniMessageForDisplay(description: String?): Component? = description?.let {
        runCatching { MiniMessage.miniMessage().deserialize(it) }.getOrElse { _ -> Component.text(it) }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
        loaded = false
    }
}
