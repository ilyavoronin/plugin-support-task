package com.h0tk3y.third.party.plugin

import com.h0tk3y.player.MusicApp
import com.h0tk3y.player.PlaybackListenerPlugin
import com.h0tk3y.player.PlaybackState
import com.h0tk3y.player.simpleStringRepresentation
import java.io.InputStream
import java.io.OutputStream

class ResumePlugin(override val musicAppInstance : MusicApp) : PlaybackListenerPlugin {
    private val playlistToLastTrack = mutableMapOf <String, String>()
    private var lastPlaylistName = "";

    override fun onPlaybackStateChange(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState) {
        when (newPlaybackState) {
            is PlaybackState.Playing -> {
                val curPlaylistName = newPlaybackState.playlistPosition.playlist.name
                if (curPlaylistName != lastPlaylistName) {
                    println("Last track: " + playlistToLastTrack[curPlaylistName])
                    lastPlaylistName = curPlaylistName
                }
                playlistToLastTrack[curPlaylistName] = newPlaybackState.playlistPosition.currentTrack.simpleStringRepresentation
            }
            is PlaybackState.Stopped -> {
                lastPlaylistName = ""
            }
        }
    }

    override fun init(persistedState: InputStream?) {
        if (persistedState != null) {
            val text = persistedState.reader().readText().lines()
            val size = text[0].toIntOrNull() ?: 0
            for (i in 1 until size * 2 + 1 step 2) {
                playlistToLastTrack[text[i]] = text[i + 1]
            }
        }
    }

    override fun persist(stateStream: OutputStream) {

        stateStream.write(buildString {
            appendln(playlistToLastTrack.size)
            playlistToLastTrack.forEach { playlistName, lastTrack ->
                appendln(playlistName)
                appendln(lastTrack)
            }
        }.toByteArray())
    }
}