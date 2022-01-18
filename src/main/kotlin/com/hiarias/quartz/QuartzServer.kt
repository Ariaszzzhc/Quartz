package com.hiarias.quartz

import com.destroystokyo.paper.entity.ai.MobGoals
import com.destroystokyo.paper.profile.PlayerProfile
import com.hiarias.quartz.mixin.DedicatedServerPropertiesAccessor
import com.hiarias.quartz.mixin.PlayerListAccessor
import io.papermc.paper.datapack.DatapackManager
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.minecraft.server.dedicated.DedicatedPlayerList
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.enchantment.Enchantments
import org.bukkit.*
import org.bukkit.advancement.Advancement
import org.bukkit.block.data.BlockData
import org.bukkit.boss.*
import org.bukkit.command.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.generator.ChunkGenerator
import org.bukkit.help.HelpMap
import org.bukkit.inventory.*
import org.bukkit.loot.LootTable
import org.bukkit.map.MapView
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginLoadOrder
import org.bukkit.plugin.ServicesManager
import org.bukkit.plugin.SimplePluginManager
import org.bukkit.plugin.java.JavaPluginLoader
import org.bukkit.plugin.messaging.Messenger
import org.bukkit.potion.PotionEffectType
import org.bukkit.scoreboard.ScoreboardManager
import org.bukkit.structure.StructureManager
import org.bukkit.util.CachedServerIcon
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.lang.IllegalArgumentException
import java.util.*
import java.util.function.Consumer
import java.util.logging.Logger

@Suppress("DEPRECATION")
class QuartzServer(
    private val console: DedicatedServer,
    private val players: DedicatedPlayerList,
) : Server {
    private val logger = QuartzMod.logger
    private val serverName = QuartzMod.MOD_ID
    private val serverVersion = QuartzMod::class.java.`package`.implementationVersion ?: "undefined"
    private val commandMap = SimpleCommandMap(this)
    private val pluginManager = SimplePluginManager(this, this.commandMap)
    private val configuration: YamlConfiguration
    private val commandsConfiguration: YamlConfiguration
    private val bukkitVersion: String = getPaperVersion()

    init {
        Bukkit.setServer(this)
        Enchantments.SHARPNESS.javaClass
        Enchantment.stopAcceptingRegistrations()

//        Potion.setPotionBrewer(QuartzPotionBrewer())
        MobEffects.BLINDNESS.javaClass
        PotionEffectType.stopAcceptingRegistrations()

        configuration = YamlConfiguration.loadConfiguration(Options.bukkitSettings)
        configuration.options().copyDefaults(true)
        configuration.setDefaults(YamlConfiguration.loadConfiguration(InputStreamReader(javaClass.classLoader.getResourceAsStream("configurations/bukkit.yml")!!, Charsets.UTF_8)))
        var legacyAlias: ConfigurationSection? = null

        if (!configuration.isString("aliases")) {
            legacyAlias = configuration.getConfigurationSection("aliases")
            configuration["aliases"] = "now-in-commands.yml"
        }
        saveConfig()
        if (Options.commandSettings.isFile) {
            legacyAlias = null
        }

        commandsConfiguration = YamlConfiguration.loadConfiguration(Options.commandSettings)
        commandsConfiguration.options().copyDefaults(true)
        commandsConfiguration.setDefaults(
            YamlConfiguration.loadConfiguration(
                InputStreamReader(
                    javaClass.classLoader.getResourceAsStream(
                        "configurations/commands.yml"
                    )!!, Charsets.UTF_8
                )
            )
        )
        saveCommandsConfig()

        if (legacyAlias != null) {
            val aliases = commandsConfiguration.createSection("aliases")
            for (key in legacyAlias.getKeys(false)) {
                val commands = ArrayList<String>()
                if (legacyAlias.isList(key!!)) {
                    for (command in legacyAlias.getStringList(key)) {
                        commands.add("$command $1-")
                    }
                } else {
                    commands.add(legacyAlias.getString(key) + " $1-")
                }
                aliases.set(key, commands)
            }
        }

        saveCommandsConfig()

        // TODO
    }

    private fun saveConfig() {
        try {
            configuration.save(Options.bukkitSettings)
        } catch (e: IOException) {
            logger.fatal("Could not save ${Options.bukkitSettings}", e)
        }
    }

    private fun saveCommandsConfig() {
        try {
            configuration.save(Options.commandSettings)
        } catch (e: IOException) {
            logger.fatal("Could not save ${Options.commandSettings}", e)
        }
    }

    fun loadPlugins() {
        pluginManager.registerInterface(JavaPluginLoader::class.java)
        val folder = Options.plugins

        if (!folder.exists()) {
            folder.mkdirs()
        }

        pluginManager.loadPlugins(folder).forEach {
            try {
                val message = "Loading ${it.description.fullName}"
                it.logger.info(message)
                it.onLoad()
            } catch (t: Throwable) {
                logger.fatal("${t.message} initializing ${it.description.fullName} (Is it up to date?)", t)
            }
        }
    }

    fun enablePlugins(order: PluginLoadOrder) {
        if (order == PluginLoadOrder.STARTUP) {
            TODO("Not yet implemented")
        }

        pluginManager.plugins.forEach {
            if (!it.isEnabled && (it.description.load == order)) {
                enablePlugin(it)
            }
        }

        if (order == PluginLoadOrder.POSTWORLD) {
            TODO("Not yet implemented")
        }
    }

    fun disablePlugins() = pluginManager.disablePlugins()

    private fun enablePlugin(plugin: Plugin) {
        try {
            plugin.description.permissions.forEach {
                try {
                    pluginManager.addPermission(it, false)
                } catch (e: IllegalArgumentException) {
                    logger.warn("Plugin ${plugin.description.fullName} tried to register permission '${it.name}' but it's already registered", e)
                }
            }
            pluginManager.dirtyPermissibles()
            pluginManager.enablePlugin(plugin)
        } catch (t: Throwable) {
            logger.fatal("${t.message} loading ${plugin.description.fullName} (Is it up to date?)", t)
        }
    }

    override fun sendPluginMessage(source: Plugin, channel: String, message: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun getListeningPluginChannels(): MutableSet<String> {
        TODO("Not yet implemented")
    }

    override fun audiences(): MutableIterable<Audience> {
        TODO("Not yet implemented")
    }

    override fun getPluginsFolder(): File {
        return Options.plugins
    }

    override fun getName(): String {
        return serverName
    }

    override fun getVersion(): String {
        return serverVersion
    }

    override fun getBukkitVersion(): String {
        return bukkitVersion
    }

    override fun getMinecraftVersion(): String {
        TODO("Not yet implemented")
    }

    override fun getOnlinePlayers(): MutableCollection<out Player> {
        TODO("Not yet implemented")
    }

    override fun getMaxPlayers() = players.maxPlayers

    override fun setMaxPlayers(maxPlayers: Int) {
        (players as PlayerListAccessor).setMaxPlayers(maxPlayers)
    }

    override fun getPort() = console.serverPort

    override fun getViewDistance() = console.properties.viewDistance

    override fun getSimulationDistance() = console.properties.simulationDistance

    override fun getIp(): String = console.serverIp

    override fun getWorldType(): String =
        (console.properties as DedicatedServerPropertiesAccessor).internalProperties.getProperty("level-type")

    override fun getGenerateStructures() =
        console.properties.getWorldGenSettings(console.registryAccess()).generateFeatures()

    override fun getMaxWorldSize() = console.properties.maxWorldSize

    override fun getAllowEnd() = configuration.getBoolean("settings.allow-end")

    override fun getAllowNether() = console.isNetherEnabled

    override fun getResourcePack(): String = console.resourcePack

    override fun getResourcePackHash(): String = console.resourcePackHash.uppercase(Locale.ROOT)

    override fun getResourcePackPrompt(): String {
        TODO("Not yet implemented")
    }

    override fun isResourcePackRequired() = console.isResourcePackRequired

    override fun hasWhitelist(): Boolean = console.properties.whiteList.get()

    override fun setWhitelist(value: Boolean) {
        TODO("Not yet implemented")
    }

    override fun isWhitelistEnforced(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setWhitelistEnforced(value: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getWhitelistedPlayers(): MutableSet<OfflinePlayer> {
        TODO("Not yet implemented")
    }

    override fun reloadWhitelist() {
        TODO("Not yet implemented")
    }

    override fun broadcastMessage(message: String): Int {
        TODO("Not yet implemented")
    }

    override fun broadcast(message: String, permission: String): Int {
        TODO("Not yet implemented")
    }

    override fun broadcast(message: Component): Int {
        TODO("Not yet implemented")
    }

    override fun broadcast(message: Component, permission: String): Int {
        TODO("Not yet implemented")
    }

    override fun getUpdateFolder(): String = configuration.getString("settings.update-folder", "update")!!

    override fun getUpdateFolderFile() = File(Options.plugins, configuration.getString("settings.update-folder", "update")!!)

    override fun getConnectionThrottle() = configuration.getLong("settings.connection-throttle")

    override fun getTicksPerAnimalSpawns() = configuration.getInt("ticks-per.animal-spawns")

    override fun getTicksPerMonsterSpawns() = configuration.getInt("ticks-per.monster-spawns")

    override fun getTicksPerWaterSpawns() = configuration.getInt("ticks-per.water-spawns")

    override fun getTicksPerWaterAmbientSpawns() = configuration.getInt("ticks-per.water-ambient-spawns")

    override fun getTicksPerWaterUndergroundCreatureSpawns() =
        configuration.getInt("ticks-per.water-underground-creature-spawns")

    override fun getTicksPerAmbientSpawns() = configuration.getInt("ticks-per.ambient-spawns")

    override fun getPlayer(name: String): Player? {
        TODO("Not yet implemented")
    }

    override fun getPlayer(id: UUID): Player? {
        TODO("Not yet implemented")
    }

    override fun getPlayerExact(name: String): Player? {
        TODO("Not yet implemented")
    }

    override fun matchPlayer(name: String): MutableList<Player> {
        TODO("Not yet implemented")
    }

    override fun getPlayerUniqueId(playerName: String): UUID? {
        TODO("Not yet implemented")
    }

    override fun getPluginManager() = pluginManager

    override fun getScheduler() = TODO("Not yet implemented")

    override fun getServicesManager(): ServicesManager {
        TODO("Not yet implemented")
    }

    override fun getWorlds(): MutableList<World> {
        TODO("Not yet implemented")
    }

    override fun createWorld(creator: WorldCreator): World? {
        TODO("Not yet implemented")
    }

    override fun unloadWorld(name: String, save: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun unloadWorld(world: World, save: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun getWorld(name: String): World? {
        TODO("Not yet implemented")
    }

    override fun getWorld(uid: UUID): World? {
        TODO("Not yet implemented")
    }

    override fun getWorld(worldKey: NamespacedKey): World? {
        TODO("Not yet implemented")
    }

    override fun getMap(id: Int): MapView? {
        TODO("Not yet implemented")
    }

    override fun createMap(world: World): MapView {
        TODO("Not yet implemented")
    }

    override fun createExplorerMap(world: World, location: Location, structureType: StructureType): ItemStack {
        TODO("Not yet implemented")
    }

    override fun createExplorerMap(
        world: World,
        location: Location,
        structureType: StructureType,
        radius: Int,
        findUnexplored: Boolean
    ): ItemStack {
        TODO("Not yet implemented")
    }

    override fun reload() {
        TODO("Not yet implemented")
    }

    override fun reloadData() {
        TODO("Not yet implemented")
    }

    override fun getLogger(): Logger {
        return Logger.getLogger(QuartzServer::class.java.name)
    }

    override fun getPluginCommand(name: String): PluginCommand? {
        TODO("Not yet implemented")
    }

    override fun savePlayers() {
        TODO("Not yet implemented")
    }

    override fun dispatchCommand(sender: CommandSender, commandLine: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun addRecipe(recipe: Recipe?): Boolean {
        TODO("Not yet implemented")
    }

    override fun getRecipesFor(result: ItemStack): MutableList<Recipe> {
        TODO("Not yet implemented")
    }

    override fun getRecipe(recipeKey: NamespacedKey): Recipe? {
        TODO("Not yet implemented")
    }

    override fun getCraftingRecipe(craftingMatrix: Array<out ItemStack>, world: World): Recipe? {
        TODO("Not yet implemented")
    }

    override fun craftItem(craftingMatrix: Array<out ItemStack>, world: World, player: Player): ItemStack {
        TODO("Not yet implemented")
    }

    override fun recipeIterator(): MutableIterator<Recipe> {
        TODO("Not yet implemented")
    }

    override fun clearRecipes() {
        TODO("Not yet implemented")
    }

    override fun resetRecipes() {
        TODO("Not yet implemented")
    }

    override fun removeRecipe(key: NamespacedKey): Boolean {
        TODO("Not yet implemented")
    }

    override fun getCommandAliases(): MutableMap<String, Array<String>> {
        TODO("Not yet implemented")
    }

    override fun getSpawnRadius(): Int {
        TODO("Not yet implemented")
    }

    override fun setSpawnRadius(value: Int) {
        TODO("Not yet implemented")
    }

    override fun getHideOnlinePlayers(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getOnlineMode(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getAllowFlight(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isHardcore(): Boolean {
        TODO("Not yet implemented")
    }

    override fun shutdown() {
        TODO("Not yet implemented")
    }

    override fun getOfflinePlayer(name: String): OfflinePlayer {
        TODO("Not yet implemented")
    }

    override fun getOfflinePlayer(id: UUID): OfflinePlayer {
        TODO("Not yet implemented")
    }

    override fun getOfflinePlayerIfCached(name: String): OfflinePlayer? {
        TODO("Not yet implemented")
    }

    override fun getIPBans(): MutableSet<String> {
        TODO("Not yet implemented")
    }

    override fun banIP(address: String) {
        TODO("Not yet implemented")
    }

    override fun unbanIP(address: String) {
        TODO("Not yet implemented")
    }

    override fun getBannedPlayers(): MutableSet<OfflinePlayer> {
        TODO("Not yet implemented")
    }

    override fun getBanList(type: BanList.Type): BanList {
        TODO("Not yet implemented")
    }

    override fun getOperators(): MutableSet<OfflinePlayer> {
        TODO("Not yet implemented")
    }

    override fun getDefaultGameMode(): GameMode {
        TODO("Not yet implemented")
    }

    override fun setDefaultGameMode(mode: GameMode) {
        TODO("Not yet implemented")
    }

    override fun getConsoleSender(): ConsoleCommandSender {
        TODO("Not yet implemented")
    }

    override fun getWorldContainer(): File {
        TODO("Not yet implemented")
    }

    override fun getOfflinePlayers(): Array<OfflinePlayer> {
        TODO("Not yet implemented")
    }

    override fun getMessenger(): Messenger {
        TODO("Not yet implemented")
    }

    override fun getHelpMap(): HelpMap {
        TODO("Not yet implemented")
    }

    override fun createInventory(owner: InventoryHolder?, type: InventoryType): Inventory {
        TODO("Not yet implemented")
    }

    override fun createInventory(owner: InventoryHolder?, type: InventoryType, title: Component): Inventory {
        TODO("Not yet implemented")
    }

    override fun createInventory(owner: InventoryHolder?, type: InventoryType, title: String): Inventory {
        TODO("Not yet implemented")
    }

    override fun createInventory(owner: InventoryHolder?, size: Int): Inventory {
        TODO("Not yet implemented")
    }

    override fun createInventory(owner: InventoryHolder?, size: Int, title: Component): Inventory {
        TODO("Not yet implemented")
    }

    override fun createInventory(owner: InventoryHolder?, size: Int, title: String): Inventory {
        TODO("Not yet implemented")
    }

    override fun createMerchant(title: Component?): Merchant {
        TODO("Not yet implemented")
    }

    override fun createMerchant(title: String?): Merchant {
        TODO("Not yet implemented")
    }

    override fun getMonsterSpawnLimit(): Int {
        TODO("Not yet implemented")
    }

    override fun getAnimalSpawnLimit(): Int {
        TODO("Not yet implemented")
    }

    override fun getWaterAnimalSpawnLimit(): Int {
        TODO("Not yet implemented")
    }

    override fun getWaterAmbientSpawnLimit(): Int {
        TODO("Not yet implemented")
    }

    override fun getWaterUndergroundCreatureSpawnLimit(): Int {
        TODO("Not yet implemented")
    }

    override fun getAmbientSpawnLimit(): Int {
        TODO("Not yet implemented")
    }

    override fun isPrimaryThread(): Boolean {
        TODO("Not yet implemented")
    }

    override fun motd(): Component {
        TODO("Not yet implemented")
    }

    override fun getMotd(): String {
        TODO("Not yet implemented")
    }

    override fun shutdownMessage(): Component? {
        TODO("Not yet implemented")
    }

    override fun getShutdownMessage(): String? {
        TODO("Not yet implemented")
    }

    override fun getWarningState(): Warning.WarningState {
        TODO("Not yet implemented")
    }

    override fun getItemFactory(): ItemFactory {
        TODO("Not yet implemented")
    }

    override fun getScoreboardManager(): ScoreboardManager {
        TODO("Not yet implemented")
    }

    override fun getServerIcon(): CachedServerIcon? {
        TODO("Not yet implemented")
    }

    override fun loadServerIcon(file: File): CachedServerIcon {
        TODO("Not yet implemented")
    }

    override fun loadServerIcon(image: BufferedImage): CachedServerIcon {
        TODO("Not yet implemented")
    }

    override fun setIdleTimeout(threshold: Int) {
        TODO("Not yet implemented")
    }

    override fun getIdleTimeout(): Int {
        TODO("Not yet implemented")
    }

    override fun createChunkData(world: World): ChunkGenerator.ChunkData {
        TODO("Not yet implemented")
    }

    override fun createVanillaChunkData(world: World, x: Int, z: Int): ChunkGenerator.ChunkData {
        TODO("Not yet implemented")
    }

    override fun createBossBar(title: String?, color: BarColor, style: BarStyle, vararg flags: BarFlag): BossBar {
        TODO("Not yet implemented")
    }

    override fun createBossBar(
        key: NamespacedKey,
        title: String?,
        color: BarColor,
        style: BarStyle,
        vararg flags: BarFlag
    ): KeyedBossBar {
        TODO("Not yet implemented")
    }

    override fun getBossBars(): MutableIterator<KeyedBossBar> {
        TODO("Not yet implemented")
    }

    override fun getBossBar(key: NamespacedKey): KeyedBossBar? {
        TODO("Not yet implemented")
    }

    override fun removeBossBar(key: NamespacedKey): Boolean {
        TODO("Not yet implemented")
    }

    override fun getEntity(uuid: UUID): Entity? {
        TODO("Not yet implemented")
    }

    override fun getTPS(): DoubleArray {
        TODO("Not yet implemented")
    }

    override fun getTickTimes(): LongArray {
        TODO("Not yet implemented")
    }

    override fun getAverageTickTime(): Double {
        TODO("Not yet implemented")
    }

    override fun getCommandMap(): CommandMap {
        TODO("Not yet implemented")
    }

    override fun getAdvancement(key: NamespacedKey): Advancement? {
        TODO("Not yet implemented")
    }

    override fun advancementIterator(): MutableIterator<Advancement> {
        TODO("Not yet implemented")
    }

    override fun createBlockData(material: Material): BlockData {
        TODO("Not yet implemented")
    }

    override fun createBlockData(material: Material, consumer: Consumer<BlockData>?): BlockData {
        TODO("Not yet implemented")
    }

    override fun createBlockData(data: String): BlockData {
        TODO("Not yet implemented")
    }

    override fun createBlockData(material: Material?, data: String?): BlockData {
        TODO("Not yet implemented")
    }

    override fun <T : Keyed?> getTag(registry: String, tag: NamespacedKey, clazz: Class<T>): Tag<T> {
        TODO("Not yet implemented")
    }

    override fun <T : Keyed?> getTags(registry: String, clazz: Class<T>): MutableIterable<Tag<T>> {
        TODO("Not yet implemented")
    }

    override fun getLootTable(key: NamespacedKey): LootTable? {
        TODO("Not yet implemented")
    }

    override fun selectEntities(sender: CommandSender, selector: String): MutableList<Entity> {
        TODO("Not yet implemented")
    }

    override fun getStructureManager(): StructureManager {
        TODO("Not yet implemented")
    }

    override fun getUnsafe(): UnsafeValues {
        TODO("Not yet implemented")
    }

    override fun spigot(): Server.Spigot {
        TODO("Not yet implemented")
    }

    override fun reloadPermissions() {
        TODO("Not yet implemented")
    }

    override fun reloadCommandAliases(): Boolean {
        TODO("Not yet implemented")
    }

    override fun suggestPlayerNamesWhenNullTabCompletions(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getPermissionMessage(): String {
        TODO("Not yet implemented")
    }

    override fun createProfile(uuid: UUID): PlayerProfile {
        TODO("Not yet implemented")
    }

    override fun createProfile(name: String): PlayerProfile {
        TODO("Not yet implemented")
    }

    override fun createProfile(uuid: UUID?, name: String?): PlayerProfile {
        TODO("Not yet implemented")
    }

    override fun getCurrentTick(): Int {
        TODO("Not yet implemented")
    }

    override fun isStopping(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getMobGoals(): MobGoals {
        TODO("Not yet implemented")
    }

    override fun getDatapackManager(): DatapackManager {
        TODO("Not yet implemented")
    }

    companion object {
        fun getPaperVersion(): String {
            var result = "Unknown-Version"
            val stream =
                Bukkit::class.java.classLoader.getResourceAsStream("META-INF/maven/io.papermc.paper/paper-api/pom.properties")
            val properties = Properties()
            if (stream != null) {
                try {
                    properties.load(stream)
                    result = properties.getProperty("version")
                } catch (ex: IOException) {
                    QuartzMod.logger.fatal("Could not get Bukkit version!", ex)
                }
            }
            return result
        }
    }
}
