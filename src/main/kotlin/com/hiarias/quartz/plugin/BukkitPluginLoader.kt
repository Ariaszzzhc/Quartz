package com.hiarias.quartz.plugin

import org.bukkit.Server
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.bukkit.plugin.InvalidDescriptionException
import org.bukkit.plugin.InvalidPluginException
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.plugin.PluginLoader
import org.bukkit.plugin.RegisteredListener
import org.bukkit.plugin.UnknownDependencyException
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import java.util.regex.Pattern

@Suppress("DEPRECATION")
class BukkitPluginLoader(val server: Server) : PluginLoader {
    private val classes = ConcurrentHashMap<String, Class<*>>()
    private val loaders = CopyOnWriteArrayList<PluginClassLoader>()

    @Throws(InvalidPluginException::class)
    override fun loadPlugin(file: File): Plugin {
        if (!file.exists()) {
            throw InvalidPluginException(FileNotFoundException("${file.name} not exist!"))
        }

        val pdf = try {
            getPluginDescription(file)
        } catch (e: InvalidDescriptionException) {
            throw InvalidPluginException(e)
        }

        val parentFile = server.pluginsFolder
        val dataFolder = File(parentFile, file.name)
        val oldDataFolder = File(parentFile, pdf.rawName)

        when {
            dataFolder == oldDataFolder -> {}
            dataFolder.isDirectory && oldDataFolder.isDirectory -> {
                server.logger.warning("While loading ${pdf.fullName} ($file) found old-data folder: `$oldDataFolder' next to the new one `$dataFolder'")
            }
            oldDataFolder.isDirectory && !dataFolder.exists() -> {
                if (!oldDataFolder.renameTo(dataFolder)) {
                    throw InvalidPluginException("Unable to rename old data folder: `$oldDataFolder' to: `$dataFolder'")
                }

                server.logger.log(Level.INFO, "While loading ${pdf.fullName} ($file) renamed data folder: `$oldDataFolder' to `$dataFolder'")
            }
        }

        if (dataFolder.exists() && !dataFolder.isDirectory) {
            throw InvalidPluginException("Projected data folder: `$dataFolder' for ${pdf.fullName} ($file) exists and is not a directory")
        }

        pdf.depend.forEach {
            if (server.pluginManager.getPlugin(it) == null) {
                throw UnknownDependencyException(it)
            }
        }

        server.unsafe.checkSupported(pdf)


    }

    override fun getPluginDescription(file: File): PluginDescriptionFile {
        TODO("Not yet implemented")
    }

    override fun getPluginFileFilters(): Array<Pattern> {
        TODO("Not yet implemented")
    }

    override fun createRegisteredListeners(
        listener: Listener,
        plugin: Plugin
    ): MutableMap<Class<out Event>, MutableSet<RegisteredListener>> {
        TODO("Not yet implemented")
    }

    override fun enablePlugin(plugin: Plugin) {
        TODO("Not yet implemented")
    }

    override fun disablePlugin(plugin: Plugin) {
        TODO("Not yet implemented")
    }

    fun getClassByName(name: String): Class<*>? {
        var cachedClass = classes[name]

        if (cachedClass != null) {
            return cachedClass
        } else {
            loaders.forEach {
                try {
                    cachedClass = it.findClass(name, false)
                } catch (_: ClassNotFoundException) {}

                if (cachedClass != null) return cachedClass
            }
        }

        return try {
            Class.forName(name)
        } catch (_: ClassNotFoundException) {
            null
        }
    }
}
