package com.hiarias.quartz.plugin

import com.google.common.io.ByteStreams
import jdk.internal.loader.URLClassPath
import org.bukkit.plugin.InvalidPluginException
import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.plugin.SimplePluginManager
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.security.AccessController
import java.security.CodeSource
import java.security.PrivilegedAction
import java.util.Collections
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.logging.Level

class PluginClassLoader(
    private val loader: BukkitPluginLoader,
    parent: ClassLoader,
    private val pdf: PluginDescriptionFile,
    private val dataFolder: File,
    private val file: File
) : URLClassLoader(arrayOf(file.toURI().toURL()), parent) {

    val jar = JarFile(file)
    private val manifest = jar.manifest
    private val url = file.toURI().toURL()
    private val classes = ConcurrentHashMap<String, Class<*>>()
    private val seenIllegalAccess: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val plugin: JavaPlugin?

    init {
        try {
            val jarClass = try {
                Class.forName(pdf.main, true, this)
            } catch (e: ClassNotFoundException) {
                throw InvalidPluginException("Cannot find main class `${pdf.main}'", e)
            }

            val pluginClass = try {
                jarClass.asSubclass(JavaPlugin::class.java)
            } catch (e: ClassCastException) {
                throw InvalidPluginException("main class `${pdf.main}' does not extend JavaPlugin")
            }

            plugin = pluginClass.getDeclaredConstructor().newInstance()
        } catch (e: IllegalAccessException) {
            throw InvalidPluginException("No public constructor", e)
        } catch (e: InstantiationException) {
            throw InvalidPluginException("Abnormal plugin type", e)
        }
    }

    override fun getResource(name: String): URL {
        return findResource(name)
    }

    override fun getResources(name: String): Enumeration<URL> {
        return findResources(name)
    }

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*>? = findClass(name, true)

    @Suppress("DEPRECATION")
    fun findClass(name: String, global: Boolean): Class<*>? {
        var result = classes[name]

        if (result == null) {
            if (global) {
                result = loader.getClassByName(name)

                if (result != null) {
                    val provider = (result.classLoader as PluginClassLoader).pdf

                    if (provider != pdf && !seenIllegalAccess.contains(provider.name)
                        && (loader.server.pluginManager as SimplePluginManager).isTransitiveDepend(pdf, provider)) {

                        seenIllegalAccess.add(provider.name)

                        plugin?.logger?.log(
                                Level.WARNING,
                                "Loaded class $name from ${provider.fullName} which is not a depend, softdepend or loadbefore of this plugin."
                            ) ?: loader.server.logger.log(
                            Level.WARNING,
                            "${pdf.name} Loaded class $name from ${provider.fullName} which is not a depend, softdepend or loadbefore of this plugin."
                        )
                    }
                }
            }

            if (result == null) {
                val path = name.replace('.', '/') + ".class"
                val entry = jar.getJarEntry(path)

                if (entry != null) {
                    var classBytes = try {
                        ByteStreams.toByteArray(jar.getInputStream(entry))
                    } catch (e: IOException) {
                        throw ClassNotFoundException(name, e)
                    }

                    classBytes = loader.server.unsafe.processClass(pdf, path, classBytes)

                    val dot = name.lastIndexOf('.')
                    if (dot != -1) {
                        val pkgName = name.substring(0, dot)
                        if (getPackage(pkgName) == null) {
                            try {
                                if (manifest != null) {
                                    definePackage(pkgName, manifest, url)
                                } else {
                                    definePackage(pkgName, null, null, null, null, null, null, null)
                                }
                            } catch (e: IllegalArgumentException) {
                                throw IllegalStateException("Cannot find package $pkgName")
                            }
                        }
                    }

                    val source = CodeSource(url, entry.codeSigners)
                    result = defineClass(name, classBytes, 0, classBytes.size, source)
                }

                result = result?: runCatching {
                        super.findClass(name)
                }.getOrNull()

                result?.let {
                    loader.setClass(name, it)
                    classes[name] = it
                }

            }
        }

        return result
    }
}
