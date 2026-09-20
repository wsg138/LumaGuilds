package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.BankWithdrawalResult
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.TransactionType
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.infrastructure.services.BankServiceBukkit
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Player-first guild bank.
 *
 * Balance and money movement are the primary task. Advanced economy tools live in one predictable
 * row instead of competing with quick actions. Custom amounts use the plugin's existing protected
 * chat-input channel so no command knowledge is required.
 */
class GuildBankMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent, ChatInputHandler {

    private val vaultInventoryManager: VaultInventoryManager by inject()
    private val lang: LangService by inject()
    private val menuFactory: MenuFactory by inject()
    private val guildService: GuildService by inject()
    private val bankService: BankService by inject()
    private val memberService: MemberService by inject()
    private val chatInputListener: ChatInputListener by inject()

    private var customInputType: TransactionType? = null
    private var lastTransactionTime = 0L

    override fun open() {
        guild = guildService.getGuild(guild.id) ?: guild
        if (!isEconomyAvailable()) {
            player.sendMessage(lang.msg("menu.bank.unavailable.title"))
            player.sendMessage(lang.msg("menu.bank.unavailable.economy"))
            player.sendMessage(lang.msg("menu.bank.unavailable.install"))
            player.sendMessage(lang.msg("menu.bank.unavailable.contact"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)
            return
        }

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.DETAIL,
                lang.guiTitle("menu.bank.title", "guild" to guild.name),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addBalanceSummary(pane)
        addQuickActions(pane)
        addAdvancedTools(pane)
        addFooter(pane)
        gui.show(player)
    }

    private fun addBalanceSummary(pane: StaticPane) {
        val playerBalance = bankService.getPlayerBalance(player.uniqueId)
        val personal = ItemStack.of(Material.GOLD_NUGGET)
            .name(lang.gui("menu.bank.balance.player", "balance" to playerBalance))
            .lore(lang.gui("menu.bank.balance.player_description"))
        pane.addItem(GuiItem(personal), 2, 0)

        val guildBalance = vaultInventoryManager.getGoldBalance(guild.id)
        val bank = ItemStack.of(Material.GOLD_BLOCK)
            .name(lang.gui("menu.bank.balance.current", "balance" to guildBalance))
            .lore(lang.gui("menu.bank.balance.title"))
        pane.addItem(GuiItem(bank), 6, 0)
    }

    private fun addQuickActions(pane: StaticPane) {
        val depositAmounts = listOf(
            Triple("menu.bank.quick.deposit.100", 100, 0),
            Triple("menu.bank.quick.deposit.1000", 1_000, 1),
            Triple("menu.bank.quick.deposit.10000", 10_000, 2),
            Triple("menu.bank.quick.deposit.all", -1, 3),
        )
        depositAmounts.forEach { (key, amount, offset) ->
            pane.addItem(quickItem(key, amount, true), 1 + offset, 1)
        }
        val customDeposit = ItemStack.of(Material.EMERALD)
            .name(lang.gui("menu.bank.custom.deposit"))
            .lore(lang.gui("menu.bank.custom.deposit_description"))
        pane.addItem(GuiItem(customDeposit) { beginCustomInput(TransactionType.DEPOSIT) }, 6, 1)

        val withdrawAmounts = listOf(
            Triple("menu.bank.quick.withdraw.100", 100, 0),
            Triple("menu.bank.quick.withdraw.1000", 1_000, 1),
            Triple("menu.bank.quick.withdraw.10000", 10_000, 2),
            Triple("menu.bank.quick.withdraw.all", -1, 3),
        )
        withdrawAmounts.forEach { (key, amount, offset) ->
            pane.addItem(quickItem(key, amount, false), 1 + offset, 2)
        }
        val customWithdraw = ItemStack.of(Material.REDSTONE)
            .name(lang.gui("menu.bank.custom.withdraw"))
            .lore(lang.gui("menu.bank.custom.withdraw_description"))
        pane.addItem(GuiItem(customWithdraw) { beginCustomInput(TransactionType.WITHDRAWAL) }, 6, 2)
    }

    private fun quickItem(key: String, amount: Int, deposit: Boolean): GuiItem {
        val item = ItemStack.of(if (deposit) Material.LIME_DYE else Material.RED_DYE)
            .name(lang.gui(key))
            .lore(
                if (amount == -1) {
                    if (deposit) lang.gui("menu.bank.quick.description.deposit_all")
                    else lang.gui("menu.bank.quick.description.withdraw_all")
                } else {
                    if (deposit) lang.gui("menu.bank.quick.description.deposit", "amount" to amount)
                    else lang.gui("menu.bank.quick.description.withdraw", "amount" to amount)
                },
            )
        return GuiItem(item) { performQuickAction(amount, deposit) }
    }

    private fun addAdvancedTools(pane: StaticPane) {
        val history = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.bank.history.title", "guild" to guild.name))
            .lore(lang.gui("menu.bank.history.open_action"))
        pane.addItem(GuiItem(history) {
            menuNavigator.openMenu(menuFactory.createGuildBankTransactionHistoryMenu(menuNavigator, player, guild))
        }, 1, 4)

        val contributions = ItemStack.of(Material.PLAYER_HEAD)
            .name(lang.gui("menu.bank.navigation.contributions_name"))
            .lore(lang.gui("menu.bank.navigation.contributions_description"))
        pane.addItem(GuiItem(contributions) {
            menuNavigator.openMenu(menuFactory.createGuildMemberContributionsMenu(menuNavigator, player, guild))
        }, 2, 4)

        val budget = ItemStack.of(Material.CHEST_MINECART)
            .name(lang.gui("menu.bank.budget.title"))
        pane.addItem(GuiItem(budget) {
            menuNavigator.openMenu(menuFactory.createGuildBankBudgetMenu(menuNavigator, player, guild))
        }, 3, 4)

        val automation = ItemStack.of(Material.COMPARATOR)
            .name(lang.gui("menu.bank.navigation.automation_name"))
            .lore(lang.gui("menu.bank.navigation.automation_description"))
        pane.addItem(GuiItem(automation) {
            menuNavigator.openMenu(menuFactory.createGuildBankAutomationMenu(menuNavigator, player, guild))
        }, 4, 4)

        val security = ItemStack.of(Material.TRIPWIRE_HOOK)
            .name(lang.gui("menu.bank.security.title"))
        pane.addItem(GuiItem(security) {
            menuNavigator.openMenu(menuFactory.createGuildBankSecurityMenu(menuNavigator, player, guild))
        }, 5, 4)

        val stats = ItemStack.of(Material.FILLED_MAP)
            .name(lang.gui("menu.bank.stats.title"))
            .lore(lang.gui("menu.bank.navigation.statistics_description"))
        pane.addItem(GuiItem(stats) {
            menuNavigator.openMenu(menuFactory.createGuildBankStatisticsMenu(menuNavigator, player, guild))
        }, 6, 4)
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.common.item.back.name"))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)

        val dashboard = ItemStack.of(Material.NETHER_STAR)
            .name(lang.gui("menu.bank.back_to_control_panel"))
            .lore(lang.gui("menu.bank.navigation.back_description"))
        pane.addItem(GuiItem(dashboard) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun performQuickAction(requested: Int, deposit: Boolean) {
        if (System.currentTimeMillis() - lastTransactionTime < 700L) return
        lastTransactionTime = System.currentTimeMillis()

        val amount = if (requested == -1) {
            if (deposit) inventoryGoldBalance().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            else maximumWithdrawableAmount()
        } else {
            requested
        }
        if (amount <= 0) {
            player.sendMessage(lang.msg("menu.bank.feedback.positive_amount_required"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)
            return
        }

        val success = if (deposit) deposit(amount) else withdraw(amount)
        player.playSound(
            player.location,
            if (success) Sound.ENTITY_EXPERIENCE_ORB_PICKUP else Sound.ENTITY_VILLAGER_NO,
            1.0f,
            if (success) 1.4f else 0.8f,
        )
        open()
    }

    private fun beginCustomInput(type: TransactionType) {
        customInputType = type
        chatInputListener.startInputMode(player, this)
        player.closeInventory()
        player.sendMessage(
            if (type == TransactionType.DEPOSIT) lang.msg("menu.bank.anvil.deposit_prompt")
            else lang.msg("menu.bank.anvil.withdraw_prompt"),
        )
        player.sendMessage(lang.msg("menu.bank.anvil.numbers_only"))
    }

    override fun onChatInput(player: Player, input: String) {
        val type = customInputType ?: return
        customInputType = null
        val amount = input.trim().replace(",", "").toIntOrNull()
        if (amount == null || amount <= 0) {
            player.sendMessage(lang.msg("menu.bank.feedback.positive_amount_required"))
            open()
            return
        }
        val success = when (type) {
            TransactionType.DEPOSIT -> deposit(amount)
            TransactionType.WITHDRAWAL -> withdraw(amount)
            TransactionType.FEE, TransactionType.DEDUCTION -> false
        }
        player.playSound(
            player.location,
            if (success) Sound.ENTITY_EXPERIENCE_ORB_PICKUP else Sound.ENTITY_VILLAGER_NO,
            1.0f,
            if (success) 1.4f else 0.8f,
        )
        open()
    }

    override fun onCancel(player: Player) {
        customInputType = null
        open()
    }

    private fun deposit(amount: Int): Boolean {
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DEPOSIT_TO_BANK)) {
            player.sendMessage(lang.msg("menu.bank.feedback.deposit_permission_denied"))
            return false
        }

        val total = inventoryGoldBalance()
        if (total < amount) {
            player.sendMessage(
                lang.msg("menu.bank.feedback.insufficient_gold", "balance" to total, "amount" to amount),
            )
            return false
        }

        return runCatching {
            var remaining = amount.toLong()
            for (slot in 0 until player.inventory.size) {
                if (remaining <= 0) break
                val item = player.inventory.getItem(slot) ?: continue
                val value = net.lumalyte.lg.application.utilities.GoldBalanceButton.calculateGoldValue(item).toLong()
                if (value <= 0) continue

                if (value <= remaining) {
                    player.inventory.setItem(slot, null)
                    remaining -= value
                } else {
                    player.inventory.setItem(slot, null)
                    val change = value - remaining
                    net.lumalyte.lg.application.utilities.GoldBalanceButton.convertToItems(change)
                        .forEach { changeItem -> player.inventory.addItem(changeItem) }
                    remaining = 0
                }
            }

            vaultInventoryManager.depositGold(guild.id, player.uniqueId, amount.toLong())
            vaultInventoryManager.forceFlush(guild.id)
            guild = guildService.getGuild(guild.id) ?: guild
            player.sendMessage(lang.msg("menu.bank.feedback.deposit_success", "amount" to amount).color(NamedTextColor.GREEN))
            true
        }.getOrElse { error ->
            player.sendMessage(lang.msg("menu.bank.feedback.deposit_error", "reason" to error.message))
            false
        }
    }

    private fun withdraw(amount: Int): Boolean {
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.WITHDRAW_FROM_BANK)) {
            player.sendMessage(lang.msg("menu.bank.feedback.withdraw_permission_denied"))
            return false
        }

        return runCatching {
            when (val outcome = bankService.withdrawOutcome(
                guild.id,
                player.uniqueId,
                amount,
                "Guild bank menu withdrawal",
            )) {
                is BankWithdrawalResult.Completed -> {
                    guild = guildService.getGuild(guild.id) ?: guild
                    player.sendMessage(
                        lang.msg(
                            "menu.bank.feedback.withdraw_success",
                            "amount" to amount,
                            "fee" to outcome.transaction.fee,
                        ).color(NamedTextColor.GREEN),
                    )
                    true
                }
                is BankWithdrawalResult.Ambiguous -> {
                    player.sendMessage(
                        lang.msg("menu.bank.feedback.withdraw_pending", "transaction" to outcome.transactionId),
                    )
                    false
                }
                else -> {
                    player.sendMessage(lang.msg("menu.bank.feedback.vault_withdraw_failed"))
                    false
                }
            }
        }.getOrElse { error ->
            player.sendMessage(lang.msg("menu.bank.feedback.withdraw_error", "reason" to error.message))
            false
        }
    }

    private fun inventoryGoldBalance(): Long {
        var total = 0L
        for (slot in 0 until player.inventory.size) {
            val item = player.inventory.getItem(slot) ?: continue
            total += net.lumalyte.lg.application.utilities.GoldBalanceButton.calculateGoldValue(item).toLong()
        }
        return total
    }

    /** Finds a whole-number payout whose fee still fits inside the current guild balance. */
    private fun maximumWithdrawableAmount(): Int {
        val balance = vaultInventoryManager.getGoldBalance(guild.id)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        if (balance <= 0) return 0

        var candidate = balance
        repeat(12) {
            val fee = bankService.calculateWithdrawalFee(guild.id, candidate)
            val next = (balance - fee).coerceAtLeast(0)
            if (next == candidate) return candidate
            candidate = next
        }
        return candidate
    }

    private fun isEconomyAvailable(): Boolean =
        (bankService as? BankServiceBukkit)?.isEconomyAvailable() ?: false

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
