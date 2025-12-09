package com.coulterpeterson.floatnative.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class VideoPlayerManager(context: Context) {
    private val _player = ExoPlayer.Builder(context).build()
    val player: Player = _player

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    init {
        _player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
        })
    }

    fun playMedia(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        _player.setMediaItem(mediaItem)
        _player.prepare()
        _player.play()
    }

    fun release() {
        _player.release()
    }
}
