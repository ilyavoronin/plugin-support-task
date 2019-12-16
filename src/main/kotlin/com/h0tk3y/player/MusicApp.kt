package com.h0tk3y.player

import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.net.URLClassLoader
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmName

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginClasses: Set<String>
) : AutoCloseable {
    fun init() {
        /**
         * TODO: Инициализировать плагины с помощью функции [MusicPlugin.init],
         *       предоставив им байтовые потоки их состояния (для тех плагинов, для которых они сохранены).
         *       Обратите внимание на cлучаи, когда необходимо выбрасывать исключения
         *       [IllegalPluginException] и [PluginClassNotFoundException].
         **/
        plugins.forEach { plugin ->
            val dataFolder = File("plugin_data")
            if (!dataFolder.exists()) {
                dataFolder.mkdir()
            }
            val pluginFile = File("plugin_data/" + plugin.pluginId)
            if (pluginFile.exists()) {
                plugin.init(pluginFile.inputStream())
            } else {
                plugin.init(null)
            }
        }
        musicLibrary // access to initialize
        player.init()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        /** TODO: Сохранить состояние плагинов с помощью [MusicPlugin.persist]. */
        plugins.forEach { plugin ->
            File("./plugin_data/" + plugin.pluginId).outputStream().use { plugin.persist(it) }
        }
    }

    fun wipePersistedPluginData() {
        plugins.forEach { plugin ->
            File("./plugin_data/").deleteRecursively()
        }
    }

    private val pluginClassLoader: ClassLoader =
        URLClassLoader(pluginClasspath.map { it.toURI().toURL() }.toTypedArray());

    private val plugins: List<MusicPlugin> by lazy {
        /**
         * TODO используя [pluginClassLoader] и следуя контракту [MusicPlugin],
         *      загрузить плагины, перечисленные в [enabledPluginClasses].
         *      Эта функция не должна вызывать [MusicPlugin.init]
         */
        enabledPluginClasses.map {
            val loadedClass = try {
                pluginClassLoader.loadClass(it)
            } catch (e: ClassNotFoundException) {
                throw PluginClassNotFoundException(it)
            }
            val kclass = loadedClass.kotlin
            if (!kclass.isSubclassOf(MusicPlugin::class)) {
                throw IllegalPluginException(loadedClass)
            }
            val primaryConstructor = kclass.primaryConstructor ?: throw IllegalPluginException(loadedClass)
            if (primaryConstructor.parameters.size == 1) {
                primaryConstructor.call(this) as MusicPlugin
            } else {
                val appProperty = kclass.memberProperties.filterIsInstance<KMutableProperty<MusicPlugin>>().find {
                    it.name == "musicAppInstance"
                } ?: throw IllegalPluginException(loadedClass)
                if (primaryConstructor.parameters.size != 0) {
                    throw IllegalPluginException(loadedClass)
                }
                val musicPl = primaryConstructor.call() as MusicPlugin
                appProperty.setter.call(musicPl, this)
                musicPl
            }

        }
    }

    fun findSinglePlugin(pluginClassName: String): MusicPlugin? =
        plugins.singleOrNull { plugin -> plugin::class.qualifiedName == pluginClassName }

    fun <T : MusicPlugin> getPlugins(pluginClass: Class<T>): List<T> =
        plugins.filterIsInstance(pluginClass)

    private val musicLibraryContributors: List<MusicLibraryContributorPlugin>
        get() = getPlugins(MusicLibraryContributorPlugin::class.java)

    protected val playbackListeners: List<PlaybackListenerPlugin>
        get() = getPlugins(PlaybackListenerPlugin::class.java)

    val musicLibrary: MusicLibrary by lazy {
        musicLibraryContributors
            .sortedWith(compareBy({ it.preferredOrder }, { it.pluginId }))
            .fold(MusicLibrary(mutableListOf())) { acc, it -> it.contribute(acc) }
    }

    open val player: MusicPlayer by lazy {
        JLayerMusicPlayer(
            playbackListeners
        )
    }

    fun startPlayback(playlist: Playlist, fromPosition: Int) {
        player.playbackState = PlaybackState.Playing(
            PlaylistPosition(
                playlist,
                fromPosition
            ), isResumed = false
        )
    }

    fun nextOrStop() = player.playbackState.playlistPosition?.let {
        val nextPosition = it.position + 1
        player.playbackState =
            if (nextPosition in it.playlist.tracks.indices)
                PlaybackState.Playing(
                    PlaylistPosition(
                        it.playlist,
                        nextPosition
                    ), isResumed = false
                )
            else
                PlaybackState.Stopped
    }

    @Volatile
    var isClosed = false
        private set
}