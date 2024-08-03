package com.task.sm.chromecast.screens

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.cast.CastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.ui.PlayerView
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.framework.CastContext
import com.task.sm.chromecast.MainActivity
import com.task.sm.chromecast.MainActivity.Companion.castContext
import com.task.sm.chromecast.manager.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class HomeViewModal(context: Context) : ViewModel() {

    var playerManager: MutableState<PlayerManager?> = mutableStateOf(null)
        private set
    var playerView by mutableStateOf<PlayerView>(PlayerView(context))
    val mSelector: MediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
        .build()

    var playerControllerUiState by mutableStateOf(PlayerController())
        private set

    var listner: PlayerManager.Listener = object : PlayerManager.Listener {
        override fun onQueuePositionChanged(previousIndex: Int, newIndex: Int) {
            // Handle queue position changes if needed

        }

        override fun onUnsupportedTrack(trackType: Int) {
            // Handle unsupported track types if needed
        }

        override fun onEvents(player: Player, events: Player.Events) {
            println("onEvents: ${player.duration}")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            println("onPlaybackStateChanged: $playbackState")
            if (playbackState == Player.STATE_READY) {
                getProgress()
            }
        }

        override fun onCastSessionAvailable() {
            println("onCastSessionAvailable")
        }

        override fun onCastSessionUnavailable() {
            println("onCastSessionUnavailable")
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            println("onPositionDiscontinuity: Old Position : ${oldPosition.mediaItemIndex}")
            println("onPositionDiscontinuity: New Position : ${newPosition.mediaItemIndex}")
        }
    }

    init {
        playerManager.value = PlayerManager(
            context,
            listner,
            playerView,
            castContext,
            false  // if you integrate autoplay pass value here
        )
    }

    fun setMediaItems(
        videos: List<VideoModal>,
        sliderPosition: Long
    ) {
        val castMediaItems = arrayListOf<MediaItem>()
        val mediaItems = arrayListOf<MediaItem>()
//        If You have list set media items here
          videos.forEach {
              val mediaItem = MediaItem.Builder()
                  .setUri(it.url)
                  .setMediaId(it.id.toString())
                  .setTag(it.title)
                  .setMimeType("video/mp4")
                  .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).build())
                  .build()
              castMediaItems.add(mediaItem)
              mediaItems.add(mediaItem)
          }
        playerManager.value?.getCurrentMediaItem()?.let { playerManager.value?.addItem(it) }
        val videoIndex = playerManager.value?.getCurrentPlayer()?.currentMediaItemIndex
        if (videoIndex != null) {
            playerManager.value?.setMediaItems(
                mediaItems,
                if (videoIndex >= 0) videoIndex else 0,
                sliderPosition,
                castMediaItems
            )
        }
    }

    fun onReleaseExoPlayer() {
        if (playerManager.value != null) {
            playerManager.value?.release()
        }
    }

    fun currentPositionFlow(
        player: Player?
    ) = flow {
        while (true) {
            if (player?.isPlaying == true) emit(player.currentPosition)
            delay(1000)
        }
    }.flowOn(Dispatchers.Main)

    fun getProgress() {
        viewModelScope.launch {

            currentPositionFlow(playerManager.value?.getCurrentPlayer()).collect {
                playerControllerUiState = playerControllerUiState.copy(
                    currentPosition = it,
                )
                println("Progress: ${it}")
            }
        }
    }
}