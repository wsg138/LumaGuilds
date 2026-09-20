package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.PerkType
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.infrastructure.services.TeleportationService
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Guild homes as an actual GUI workflow.
 *
 * Naming a new home is the only text-input step. Players never have to discover a command in order
 * to create, browse, move, remove, configure or use a guild home.
 */
class GuildHomeMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent, ChatInputHandler {

    private val guildService: GuildService by inject()
    private val configService: ConfigService by inject()
    private val progressionService: ProgressionService by inject()
    private val teleportationService: TeleportationService by inject()
    private val rankService: RankService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    private var awaitingHomeName = false

    override fun open() {
        guild = guildService.getGuild(guild.id) ?: guild
        val homes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)
        val canManage = rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.LIST,
                lang.guiTitle("menu.guild_home.title", "guild" to guild.name),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val summary = ItemStack.of(Material.COMPASS)
            .name(lang.gui("menu.guild_home.slots.name"))
            .lore(lang.gui("menu.guild_home.slots.count", "count" to homes.size, "total" to availableSlots))
            .lore(Component.text("Select a home to teleport or manage it.", NamedTextColor.GRAY))
        pane.addItem(GuiItem(summary), 4, 0)

        homes.homes.entries.take(27).forEachIndexed { index, (homeName, home) ->
            val worldName = Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown")
            val item = ItemStack.of(if (homeName == "main") Material.RECOVERY_COMPASS else Material.ENDER_PEARL)
                .name(Component.text(if (homeName == "main") "Main Home" else homeName, NamedTextColor.WHITE))
                .lore(lang.gui("menu.guild_home.world", "world" to worldName))
                .lore(Component.text("Click to open", NamedTextColor.AQUA))
            pane.addItem(GuiItem(item) {
                menuNavigator.openMenu(GuildHomeDetailMenu(menuNavigator, player, guild, homeName, home))
            }, index % 9, 1 + index / 9)
        }

        if (homes.isEmpty()) {
            val empty = ItemStack.of(Material.GRAY_DYE)
                .name(lang.gui("menu.guild_home.teleport.none.name"))
                .lore(lang.gui("menu.guild_home.teleport.none.description"))
            pane.addItem(GuiItem(empty), 4, 2)
        }

        val hasRoom = homes.size < availableSlots
        val mainMissing = !homes.homes.containsKey("main")
        val setMain = ItemStack.of(
            if (canManage && hasRoom && mainMissing) Material.LIME_DYE else Material.GRAY_DYE,
        )
            .name(lang.gui("menu.guild_home.set.main.name"))
            .lore(
                when {
                    !canManage -> Component.text("Requires Manage Home permission.", NamedTextColor.RED)
                    !mainMissing -> Component.text("Main home is already set. Open it to move it.", NamedTextColor.GRAY)
                    !hasRoom -> Component.text("No home slots are available.", NamedTextColor.RED)
                    else -> Component.text("Set the main home at your current location.", NamedTextColor.GRAY)
                },
            )
        pane.addItem(GuiItem(setMain) {
            if (canManage && hasRoom && mainMissing) setHome("main")
        }, 1, 4)

        val addNamed = ItemStack.of(if (canManage && hasRoom) Material.NAME_TAG else Material.GRAY_DYE)
            .name(Component.text("Add Named Home", NamedTextColor.WHITE))
            .lore(
                when {
                    !canManage -> Component.text("Requires Manage Home permission.", NamedTextColor.RED)
                    !hasRoom -> Component.text("No home slots are available.", NamedTextColor.RED)
                    else -> Component.text("Choose a name, then save your current location.", NamedTextColor.GRAY)
                },
            )
            .lore(if (canManage && hasRoom) Component.text("Click to name the home", NamedTextColor.AQUA) else Component.empty())
        pane.addItem(GuiItem(addNamed) {
            if (canManage && hasRoom) startHomeNameInput()
        }, 3, 4)

        val allyUnlocked = progressionService.hasPerkUnlocked(guild.id, PerkType.ALLY_HOME_ACCESS)
        val allyHomes = guildService.getAllyHomes(guild.id)
        val allies = ItemStack.of(if (allyUnlocked) Material.ENDER_EYE else Material.GRAY_DYE)
            .name(Component.text("Ally Homes", NamedTextColor.WHITE))
            .lore(
                if (allyUnlocked) Component.text("${allyHomes.size} available ally homes", NamedTextColor.GRAY)
                else Component.text("Unlock Ally Home Access through guild progression.", NamedTextColor.RED),
            )
            .lore(if (allyUnlocked) Component.text("Click to browse", NamedTextColor.AQUA) else Component.empty())
        pane.addItem(GuiItem(allies) {
            if (allyUnlocked) menuNavigator.openMenu(GuildAllyHomesMenu(menuNavigator, player, guild))
        }, 5, 4)

        val claimsEnabled = configService.loadConfig().claimsEnabled
        val claims = ItemStack.of(if (claimsEnabled) Material.GRASS_BLOCK else Material.GRAY_DYE)
            .name(Component.text("Claims & Territory", NamedTextColor.WHITE))
            .lore(
                if (claimsEnabled) Component.text("Open claim, trust and flag tools.", NamedTextColor.GRAY)
                else Component.text("Claims are disabled on this server.", NamedTextColor.GRAY),
            )
        pane.addItem(GuiItem(claims) {
            if (claimsEnabled) menuNavigator.openMenu(menuFactory.createClaimListMenu(menuNavigator, player))
        }, 7, 4)

        addFooter(pane)
        gui.show(player)
    }

    private fun startHomeNameInput() {
        awaitingHomeName = true
        chatInputListener.startInputMode(player, this)
        player.closeInventory()
        player.sendMessage(Component.text("Name this guild home in chat.", NamedTextColor.AQUA))
        player.sendMessage(Component.text("Use 1-24 letters, numbers, _ or -. Type cancel to go back.", NamedTextColor.GRAY))
    }

    override fun onChatInput(player: Player, input: String) {
        if (!awaitingHomeName) return
        awaitingHomeName = false

        val name = input.trim()
        if (!name.matches(Regex("^[A-Za-z0-9_-]{1,24}$"))) {
            player.sendMessage(Component.text("Home names must be 1-24 letters, numbers, _ or -.", NamedTextColor.RED))
            open()
            return
        }
        if (guildService.getHomes(guild.id).homes.keys.any { it.equals(name, ignoreCase = true) }) {
            player.sendMessage(Component.text("A guild home with that name already exists.", NamedTextColor.RED))
            open()
            return
        }
        setHome(name)
    }

    override fun onCancel(player: Player) {
        awaitingHomeName = false
        open()
    }

    private fun setHome(homeName: String) {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage(Component.text("You do not have permission to manage guild homes.", NamedTextColor.RED))
            open()
            return
        }

        val location = player.location
        if (configService.loadConfig().guild.homeTeleportSafetyCheck && !isLocationSafe(location)) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.unsafe_set"))
            player.sendMessage(lang.msg("menu.guild_home.feedback.safety_hint"))
            open()
            return
        }

        val home = GuildHome(
            worldId = location.world.uid,
            position = Position3D(location.x.toInt(), location.y.toInt(), location.z.toInt()),
        )
        if (guildService.setHome(guild.id, homeName, home, player.uniqueId)) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.set", "home" to homeName))
            guild = guildService.getGuild(guild.id) ?: guild
        } else {
            player.sendMessage(lang.msg("menu.guild_home.feedback.set_failed"))
        }
        open()
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW).name(lang.gui("menu.common.item.back.name"))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)

        val dashboard = ItemStack.of(Material.NETHER_STAR)
            .name(Component.text("Guild Home", NamedTextColor.AQUA))
            .lore(Component.text("Return to the main guild dashboard.", NamedTextColor.GRAY))
        pane.addItem(GuiItem(dashboard) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun isLocationSafe(location: Location): Boolean {
        val block = location.block
        val below = location.clone().subtract(0.0, 1.0, 0.0).block
        val above = location.clone().add(0.0, 1.0, 0.0).block
        val dangerous = setOf(
            Material.LAVA,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.CACTUS,
            Material.SWEET_BERRY_BUSH,
            Material.POINTED_DRIPSTONE,
            Material.MAGMA_BLOCK,
        )
        val safeGround = below.type.isSolid && below.type !in dangerous
        val bodySpace = !block.type.isSolid && block.type !in dangerous
        val headSpace = !above.type.isSolid && above.type !in dangerous
        return safeGround && bodySpace && headSpace
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

/** Detail/actions for one named guild home. */
class GuildHomeDetailMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val homeName: String,
    private var home: GuildHome,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val configService: ConfigService by inject()
    private val teleportationService: TeleportationService by inject()
    private val rankService: RankService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val current = guildService.getHomes(guild.id).homes[homeName]
        if (current == null) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }
        home = current
        val canManage = rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)
        val worldName = Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown")

        val gui = ChestGui(6, MenuTitleBuilder.redesign(MenuSurface.DETAIL, homeName))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val info = ItemStack.of(if (homeName == "main") Material.RECOVERY_COMPASS else Material.COMPASS)
            .name(Component.text(if (homeName == "main") "Main Home" else homeName, NamedTextColor.WHITE))
            .lore(lang.gui("menu.guild_home.world", "world" to worldName))
            .lore(Component.text("X ${home.position.x}  Y ${home.position.y}  Z ${home.position.z}", NamedTextColor.GRAY))
        pane.addItem(GuiItem(info), 4, 1)

        val teleport = ItemStack.of(Material.ENDER_PEARL)
            .name(Component.text("Teleport", NamedTextColor.WHITE))
            .lore(Component.text("Travel to this guild home.", NamedTextColor.GRAY))
            .lore(Component.text("Click to start teleport", NamedTextColor.AQUA))
        pane.addItem(GuiItem(teleport) { startTeleport() }, 1, 3)

        val move = ItemStack.of(if (canManage) Material.RECOVERY_COMPASS else Material.GRAY_DYE)
            .name(Component.text("Move Home Here", NamedTextColor.WHITE))
            .lore(
                if (canManage) Component.text("Replace this home's location with where you are standing.", NamedTextColor.GRAY)
                else Component.text("Requires Manage Home permission.", NamedTextColor.RED),
            )
        pane.addItem(GuiItem(move) {
            if (canManage) moveHomeHere()
        }, 3, 3)

        val access = ItemStack.of(if (canManage) Material.IRON_DOOR else Material.GRAY_DYE)
            .name(Component.text("Home Access", NamedTextColor.WHITE))
            .lore(
                if (canManage) Component.text("Choose which ranks may use this home.", NamedTextColor.GRAY)
                else Component.text("Requires Manage Home permission.", NamedTextColor.RED),
            )
        pane.addItem(GuiItem(access) {
            if (canManage) {
                menuNavigator.openMenu(menuFactory.createHomeAccessMenu(menuNavigator, player, guild, homeName))
            }
        }, 5, 3)

        val remove = ItemStack.of(if (canManage) Material.TNT else Material.GRAY_DYE)
            .name(Component.text("Remove Home", if (canManage) NamedTextColor.RED else NamedTextColor.GRAY))
            .lore(
                if (canManage) Component.text("Delete this saved guild-home location.", NamedTextColor.GRAY)
                else Component.text("Requires Manage Home permission.", NamedTextColor.RED),
            )
        pane.addItem(GuiItem(remove) {
            if (canManage) confirmRemoval()
        }, 7, 3)

        addFooter(pane)
        gui.show(player)
    }

    private fun startTeleport() {
        val world = Bukkit.getWorld(home.worldId)
        if (world == null) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.world_missing"))
            return
        }
        val target = Location(
            world,
            home.position.x.toDouble() + 0.5,
            home.position.y.toDouble(),
            home.position.z.toDouble() + 0.5,
            player.location.yaw,
            player.location.pitch,
        )
        if (configService.loadConfig().guild.homeTeleportSafetyCheck && !isLocationSafe(target)) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.unsafe_teleport"))
            player.sendMessage(lang.msg("menu.guild_home.feedback.safety_hint"))
            return
        }
        player.closeInventory()
        teleportationService.startTeleport(player, target)
    }

    private fun moveHomeHere() {
        val location = player.location
        if (configService.loadConfig().guild.homeTeleportSafetyCheck && !isLocationSafe(location)) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.unsafe_set"))
            player.sendMessage(lang.msg("menu.guild_home.feedback.safety_hint"))
            return
        }
        val moved = GuildHome(
            worldId = location.world.uid,
            position = Position3D(location.x.toInt(), location.y.toInt(), location.z.toInt()),
        )
        if (guildService.setHome(guild.id, homeName, moved, player.uniqueId)) {
            home = moved
            player.sendMessage(lang.msg("menu.guild_home.feedback.set", "home" to homeName))
            open()
        } else {
            player.sendMessage(lang.msg("menu.guild_home.feedback.set_failed"))
        }
    }

    private fun confirmRemoval() {
        menuNavigator.openMenu(
            menuFactory.createConfirmationMenu(
                menuNavigator,
                player,
                "Remove $homeName?",
                "This removes the saved home location. This cannot be undone.",
                callback = {
                    if (guildService.removeHome(guild.id, homeName, player.uniqueId)) {
                        player.sendMessage(lang.msg("menu.guild_home.feedback.removed", "home" to homeName))
                    } else {
                        player.sendMessage(lang.msg("menu.guild_home.feedback.remove_failed", "home" to homeName))
                    }
                    menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
                },
            ),
        )
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW).name(lang.gui("menu.common.item.back.name"))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }, 0, 5)

        val dashboard = ItemStack.of(Material.NETHER_STAR).name(Component.text("Guild Home", NamedTextColor.AQUA))
        pane.addItem(GuiItem(dashboard) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun isLocationSafe(location: Location): Boolean {
        val block = location.block
        val below = location.clone().subtract(0.0, 1.0, 0.0).block
        val above = location.clone().add(0.0, 1.0, 0.0).block
        val dangerous = setOf(
            Material.LAVA,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.CACTUS,
            Material.SWEET_BERRY_BUSH,
            Material.POINTED_DRIPSTONE,
            Material.MAGMA_BLOCK,
        )
        return below.type.isSolid && below.type !in dangerous &&
            !block.type.isSolid && block.type !in dangerous &&
            !above.type.isSolid && above.type !in dangerous
    }

    override fun passData(data: Any?) = Unit
}

/** Browse ally homes without a chat list. */
class GuildAllyHomesMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val configService: ConfigService by inject()
    private val teleportationService: TeleportationService by inject()
    private val rankService: RankService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.redesign(MenuSurface.LIST, "Ally Homes"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val allyHomes = guildService.getAllyHomes(guild.id).entries.toList()
        if (allyHomes.isEmpty()) {
            val empty = ItemStack.of(Material.GRAY_DYE)
                .name(lang.gui("menu.guild_home.ally.none.name"))
                .lore(lang.gui("menu.guild_home.ally.none.home"))
            pane.addItem(GuiItem(empty), 4, 2)
        } else {
            allyHomes.take(36).forEachIndexed { index, (guildName, home) ->
                val targetGuild = guildService.getGuildByName(guildName)
                val allowed = targetGuild != null &&
                    guildService.canUseAllyHome(player.uniqueId, guild.id, targetGuild.id)
                val worldName = Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown")
                val item = ItemStack.of(if (allowed) Material.ENDER_EYE else Material.GRAY_DYE)
                    .name(
                        if (allowed) lang.gui("menu.guild_home.ally.name", "guild" to guildName)
                        else lang.gui("menu.guild_home.ally.locked", "guild" to guildName),
                    )
                    .lore(lang.gui("menu.guild_home.world", "world" to worldName))
                    .lore(
                        if (allowed) Component.text("Click to teleport", NamedTextColor.AQUA)
                        else lang.gui("menu.guild_home.ally.denied"),
                    )
                pane.addItem(GuiItem(item) {
                    if (allowed) startTeleport(home)
                }, index % 9, index / 9)
            }
        }

        val canManage = rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)
        val access = ItemStack.of(if (canManage) Material.IRON_DOOR else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_home.ally_access.name"))
            .lore(
                if (canManage) lang.gui("menu.guild_home.ally_access.description")
                else Component.text("Requires Manage Home permission.", NamedTextColor.RED),
            )
        pane.addItem(GuiItem(access) {
            if (canManage) menuNavigator.openMenu(menuFactory.createAllyHomeAccessMenu(menuNavigator, player, guild))
        }, 6, 5)

        val back = ItemStack.of(Material.ARROW).name(lang.gui("menu.common.item.back.name"))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }, 0, 5)
        val dashboard = ItemStack.of(Material.NETHER_STAR).name(Component.text("Guild Home", NamedTextColor.AQUA))
        pane.addItem(GuiItem(dashboard) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)
        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
        gui.show(player)
    }

    private fun startTeleport(home: GuildHome) {
        val world = Bukkit.getWorld(home.worldId)
        if (world == null) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.world_missing"))
            return
        }
        val target = Location(
            world,
            home.position.x.toDouble() + 0.5,
            home.position.y.toDouble(),
            home.position.z.toDouble() + 0.5,
            player.location.yaw,
            player.location.pitch,
        )
        if (configService.loadConfig().guild.homeTeleportSafetyCheck && !isLocationSafe(target)) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.unsafe_teleport"))
            return
        }
        player.closeInventory()
        teleportationService.startTeleport(player, target)
    }

    private fun isLocationSafe(location: Location): Boolean {
        val block = location.block
        val below = location.clone().subtract(0.0, 1.0, 0.0).block
        val above = location.clone().add(0.0, 1.0, 0.0).block
        val dangerous = setOf(Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.CACTUS, Material.MAGMA_BLOCK)
        return below.type.isSolid && below.type !in dangerous &&
            !block.type.isSolid && block.type !in dangerous &&
            !above.type.isSolid && above.type !in dangerous
    }

    override fun passData(data: Any?) = Unit
}
