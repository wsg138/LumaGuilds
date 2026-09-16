package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Banner editor on the shared detail surface while preserving the real slot-11 drop target. */
class GuildBannerMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val bankService: BankService by inject()
    private val physicalCurrencyService: PhysicalCurrencyService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    companion object {
        /** Inventory identity map used by BannerSelectionListener. */
        val activeMenus: MutableMap<UUID, Inventory> = ConcurrentHashMap()
        private const val BANNER_SLOT = 11
    }

    override fun open() {
        guild = guildService.getGuild(guild.id) ?: guild
        activeMenus.remove(player.uniqueId)

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.DETAIL,
                lang.guiTitle("menu.guild_banner.title", "guild" to guild.name),
            ),
        )
        val pane = StaticPane(0, 0, 9, 6)

        // Slot 11 (x=2,y=1) intentionally remains interactive; BannerSelectionListener owns it.
        gui.setOnTopClick { event ->
            if (event.slot != BANNER_SLOT) event.isCancelled = true
        }
        gui.setOnBottomClick { event ->
            if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                event.isCancelled = true
            }
        }
        gui.addPane(pane)

        addBannerSelectionSlot(pane, 2, 1)
        addCurrentBannerDisplay(pane, 4, 1)
        addPlacementHelp(pane, 6, 1)
        addApplyChangesButton(pane, 2, 3)
        addClearBannerButton(pane, 4, 3)
        addGetBannerCopyButton(pane, 6, 3)
        addFooter(pane)

        gui.show(player)
        activeMenus[player.uniqueId] = gui.getInventory()
    }

    private fun addCurrentBannerDisplay(pane: StaticPane, x: Int, y: Int) {
        val currentItem = guild.banner?.let { bannerData ->
            val bannerItem = bannerData.deserializeToItemStack()
            if (bannerItem != null) {
                bannerItem.clone()
                    .name(lang.gui("menu.guild_banner.current.name"))
                    .lore(lang.gui("menu.guild_banner.current.description"))
            } else {
                ItemStack.of(Material.WHITE_BANNER)
                    .name(lang.gui("menu.guild_banner.current.error"))
                    .lore(lang.gui("menu.guild_banner.current.load_failed"))
                    .lore(lang.gui("menu.guild_banner.current.contact"))
            }
        } ?: ItemStack.of(Material.WHITE_BANNER)
            .name(lang.gui("menu.guild_banner.current.none"))
            .lore(lang.gui("menu.guild_banner.current.not_configured"))

        pane.addItem(GuiItem(currentItem), x, y)
    }

    private fun addPlacementHelp(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.LOOM)
            .name(lang.gui("menu.guild_banner.slot.name"))
            .lore(lang.gui("menu.guild_banner.slot.place"))
            .lore(lang.gui("menu.guild_banner.slot.set"))
            .lore(lang.gui("menu.guild_banner.slot.supported"))
        pane.addItem(GuiItem(item), x, y)
    }

    private fun addBannerSelectionSlot(pane: StaticPane, x: Int, y: Int) {
        val placeholderItem = ItemStack.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
            .name(lang.gui("menu.guild_banner.slot.name"))
            .lore(lang.gui("menu.guild_banner.slot.description"))
        pane.addItem(GuiItem(placeholderItem), x, y)
    }

    private fun addClearBannerButton(pane: StaticPane, x: Int, y: Int) {
        val hasBanner = guild.banner != null
        val clearItem = ItemStack.of(if (hasBanner) Material.BARRIER else Material.GRAY_DYE)
            .name(if (hasBanner) lang.gui("menu.guild_banner.clear.name") else lang.gui("menu.guild_banner.clear.disabled"))
            .apply {
                if (hasBanner) {
                    lore(lang.gui("menu.guild_banner.clear.description"))
                    lore(lang.gui("menu.guild_banner.clear.fallback"))
                    lore(lang.gui("menu.guild_banner.clear.warning"))
                } else {
                    lore(lang.gui("menu.guild_banner.clear.none"))
                }
            }

        pane.addItem(GuiItem(clearItem) {
            if (!hasBanner) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.no_banner_clear"))
                return@GuiItem
            }
            if (guildService.setBanner(guild.id, null, player.uniqueId)) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.cleared"))
                guild = guildService.getGuild(guild.id) ?: guild
                open()
            } else {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.clear_failed"))
            }
        }, x, y)
    }

    private fun addApplyChangesButton(pane: StaticPane, x: Int, y: Int) {
        val applyItem = ItemStack.of(Material.LIME_CONCRETE)
            .name(lang.gui("menu.guild_banner.apply.name"))
            .lore(lang.gui("menu.guild_banner.apply.place"))
            .lore(lang.gui("menu.guild_banner.apply.click"))

        pane.addItem(GuiItem(applyItem) {
            val inventory = player.openInventory.topInventory
            val bannerItem = inventory.getItem(BANNER_SLOT)
            if (bannerItem == null || !bannerItem.type.name.endsWith("_BANNER")) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.place_first"))
                return@GuiItem
            }

            val bannerToSave = bannerItem.clone()
            if (!guildService.setBanner(guild.id, bannerToSave, player.uniqueId)) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.set_failed"))
                return@GuiItem
            }

            player.sendMessage(
                lang.msg(
                    "menu.guild_banner.feedback.set",
                    "banner" to bannerToSave.type.name.lowercase().replace("_", " "),
                ),
            )
            val remaining = player.inventory.addItem(bannerToSave)
            if (remaining.isNotEmpty()) {
                player.world.dropItem(player.location, bannerToSave)
                player.sendMessage(lang.msg("menu.guild_banner.feedback.dropped"))
            }
            inventory.setItem(BANNER_SLOT, ItemStack.of(Material.AIR))
            guild = guildService.getGuild(guild.id) ?: guild
            open()
        }, x, y)
    }

    private fun addGetBannerCopyButton(pane: StaticPane, x: Int, y: Int) {
        val config = configService.loadConfig().guild
        if (!config.bannerCopyEnabled) {
            val disabled = ItemStack.of(Material.GRAY_DYE)
                .name(lang.gui("menu.guild_banner.copy.name"))
                .lore(lang.gui("menu.guild_banner.copy.description"))
            pane.addItem(GuiItem(disabled), x, y)
            return
        }

        val bannerCopyCost = config.bannerCopyCost
        val chargeGuildBank = config.bannerCopyChargeGuildBank
        val bannerCopyFree = config.bannerCopyFree
        val useItemCost = config.bannerCopyUseItemCost
        val itemMaterial = config.bannerCopyItemMaterial
        val itemAmount = config.bannerCopyItemAmount
        val itemCustomModelData = config.bannerCopyItemCustomModelData

        val copyItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.gui("menu.guild_banner.copy.name"))
            .lore(lang.gui("menu.guild_banner.copy.description"))

        if (bannerCopyFree) {
            copyItem.lore(lang.gui("menu.guild_banner.copy.free"))
        } else if (useItemCost) {
            try {
                val material = Material.valueOf(itemMaterial.uppercase())
                copyItem.lore(
                    lang.gui(
                        "menu.guild_banner.copy.item_cost",
                        "amount" to itemAmount,
                        "material" to material.name.lowercase().replace("_", " "),
                    ),
                ).lore(lang.gui("menu.guild_banner.copy.inventory"))
            } catch (_: IllegalArgumentException) {
                copyItem.lore(lang.gui("menu.guild_banner.copy.invalid_material"))
            }
        } else if (chargeGuildBank && physicalCurrencyService.isPhysicalCurrencyEnabled()) {
            val physicalCost = config.bannerCopyPhysicalCost
            val materialName = physicalCurrencyService.getCurrencyMaterialName()
            copyItem.lore(
                lang.gui(
                    "menu.guild_banner.copy.item_cost",
                    "amount" to physicalCost,
                    "material" to materialName.lowercase().replace("_", " "),
                ),
            ).lore(lang.gui("menu.guild_banner.copy.guild_vault"))
        } else {
            copyItem.lore(lang.gui("menu.guild_banner.copy.coin_cost", "amount" to bannerCopyCost))
                .lore(
                    if (chargeGuildBank) lang.gui("menu.guild_banner.copy.guild_bank")
                    else lang.gui("menu.guild_banner.copy.personal"),
                )
            if (chargeGuildBank) {
                val fee = bankService.calculateWithdrawalFee(guild.id, bannerCopyCost)
                if (fee > 0) {
                    copyItem.lore(
                        lang.gui(
                            "menu.guild_banner.copy.total",
                            "total" to bannerCopyCost + fee,
                            "fee" to fee,
                        ),
                    )
                }
            }
        }

        pane.addItem(GuiItem(copyItem) {
            val bannerData = guild.banner
            if (bannerData == null) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.no_banner"))
                return@GuiItem
            }
            val bannerItem = bannerData.deserializeToItemStack()
            if (bannerItem == null) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.load_failed"))
                return@GuiItem
            }
            if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_BANNER)) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.no_copy_permission"))
                return@GuiItem
            }

            val paid = when {
                bannerCopyFree -> true
                useItemCost -> payWithItems(itemMaterial, itemAmount, itemCustomModelData)
                chargeGuildBank -> payFromGuild(config.bannerCopyPhysicalCost, bannerCopyCost)
                else -> payFromPlayer(bannerCopyCost)
            }
            if (!paid) return@GuiItem

            val bannerCopy = bannerItem.clone()
            val meta = bannerCopy.itemMeta
            if (meta != null) {
                meta.persistentDataContainer.set(
                    PluginKeys.GUILD_BANNER_MARKER,
                    PersistentDataType.BYTE,
                    1.toByte(),
                )
                bannerCopy.itemMeta = meta
            }

            val remaining = player.inventory.addItem(bannerCopy)
            if (remaining.isNotEmpty()) {
                player.world.dropItem(player.location, bannerCopy)
                player.sendMessage(lang.msg("menu.guild_banner.feedback.dropped"))
            }

            when {
                bannerCopyFree -> player.sendMessage(lang.msg("menu.guild_banner.feedback.free_copy"))
                useItemCost -> player.sendMessage(lang.msg("menu.guild_banner.feedback.copy_received"))
                else -> player.sendMessage(lang.msg("menu.guild_banner.feedback.copy_purchased", "amount" to bannerCopyCost))
            }
            player.sendMessage(lang.msg("menu.guild_banner.feedback.added"))
        }, x, y)
    }

    private fun payWithItems(materialName: String, amount: Int, customModelData: Int?): Boolean {
        val material = try {
            Material.valueOf(materialName.uppercase())
        } catch (_: IllegalArgumentException) {
            player.sendMessage(lang.msg("menu.guild_banner.feedback.invalid_material"))
            return false
        }

        val requiredItem = ItemStack.of(material, amount)
        @Suppress("DEPRECATION")
        if (customModelData != null) {
            val meta = requiredItem.itemMeta
            if (meta != null) {
                meta.setCustomModelData(customModelData)
                requiredItem.itemMeta = meta
            }
        }

        if (!player.inventory.containsAtLeast(requiredItem, amount)) {
            player.sendMessage(
                lang.msg(
                    "menu.guild_banner.feedback.insufficient_items",
                    "amount" to amount,
                    "material" to material.name.lowercase().replace("_", " "),
                ),
            )
            return false
        }
        player.inventory.removeItem(requiredItem)
        player.sendMessage(
            lang.msg(
                "menu.guild_banner.feedback.paid_items",
                "amount" to amount,
                "material" to material.name.lowercase().replace("_", " "),
            ),
        )
        return true
    }

    private fun payFromGuild(physicalCost: Long, virtualCost: Long): Boolean {
        if (physicalCurrencyService.isPhysicalCurrencyEnabled()) {
            val currentBalance = physicalCurrencyService.calculateVaultCurrencyValue(guild)
            if (currentBalance < physicalCost) {
                player.sendMessage(
                    lang.msg(
                        "menu.guild_banner.feedback.insufficient_vault",
                        "need" to physicalCost,
                        "have" to currentBalance,
                    ),
                )
                return false
            }
            if (!physicalCurrencyService.deductCurrency(guild, physicalCost, "Banner copy purchase")) {
                player.sendMessage(lang.msg("menu.guild_banner.feedback.vault_deduct_failed"))
                return false
            }
            return true
        }

        val fee = bankService.calculateWithdrawalFee(guild.id, virtualCost)
        val totalCost = virtualCost + fee
        val guildBalance = bankService.getBalance(guild.id)
        if (guildBalance < totalCost) {
            player.sendMessage(
                lang.msg(
                    "menu.guild_banner.feedback.insufficient_bank",
                    "need" to totalCost,
                    "have" to guildBalance,
                ),
            )
            return false
        }
        return bankService.deductFromGuildBank(guild.id, totalCost, "Banner copy purchase")
    }

    private fun payFromPlayer(cost: Long): Boolean {
        val playerBalance = bankService.getPlayerBalance(player.uniqueId)
        if (playerBalance < cost.toInt()) {
            player.sendMessage(
                lang.msg(
                    "menu.guild_banner.feedback.insufficient_coins",
                    "need" to cost,
                    "have" to playerBalance,
                ),
            )
            return false
        }
        return bankService.withdrawPlayer(player.uniqueId, cost, "Banner copy purchase")
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_banner.back.name"))
            .lore(lang.gui("menu.guild_banner.back.description"))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)

        val home = ItemStack.of(Material.COMPASS).name(lang.gui("menu.control_panel.item.home.name"))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
