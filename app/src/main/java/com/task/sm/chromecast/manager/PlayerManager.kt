package com.task.sm.chromecast.manager

import android.annotation.SuppressLint
import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.content.res.ResourcesCompat
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.framework.CastContext
import com.task.sm.chromecast.R

@SuppressLint("UnsafeOptInUsageError")
class PlayerManager(
    private val context: Context,
    private val listener: Listener,
    private val playerView: PlayerView,
    castContext: CastContext?,
    private val autoPlay: Boolean
) : Player.Listener, SessionAvailabilityListener {
    /**
     * Listener for events.
     */
    interface Listener {
        /**
         * Called when the currently played item of the media queue changes.
         */
        fun onQueuePositionChanged(previousIndex: Int, newIndex: Int)

        /**
         * Called when a track of type `trackType` is not supported by the player.
         *
         * @param trackType One of the [C]`.TRACK_TYPE_*` constants.
         */
        fun onUnsupportedTrack(trackType: Int)

        fun onEvents(player: Player, events: Player.Events)

        fun onPlaybackStateChanged(playbackState: @Player.State Int)
        fun onCastSessionAvailable()
        fun onCastSessionUnavailable()

        fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: @Player.DiscontinuityReason Int
        )
    }

    private var localPlayer: Player? = null
    private val castPlayer: CastPlayer
    private var mediaQueue = ArrayList<MediaItem>()
    private var castMediaQueue = ArrayList<MediaItem>()

    private var lastSeenTracks: Tracks? = null

    /**
     * Returns the index of the currently played item.
     */
    var currentItemIndex: Int
        private set


    private var currentPlayer: Player? = null

    //    var mPlayerView: PlayerView? = null
    private val mediaSession: MediaSessionCompat
    private var mediaItemIndex = -1
    private var previousPlayerPlaybackPosition = 0L

    init {
        currentItemIndex = C.INDEX_UNSET
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        localPlayer = ExoPlayer.Builder(context).build().also { exoPlayer ->
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            exoPlayer.pauseAtEndOfMediaItems = autoPlay.not()
            exoPlayer.setAudioAttributes(audioAttributes, true)
        }
        localPlayer?.addListener(this)

        castPlayer = castContext?.let {
            CastPlayer(it).apply {
                addListener(this@PlayerManager)
                setSessionAvailabilityListener(this@PlayerManager)
            }
        }!!

        mediaSession = MediaSessionCompat(context, "PlayerManagerMediaSession")
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )
        mediaSession.setCallback(MediaSessionCallback())
        mediaSession.isActive = true
        // Initialize MediaSessionConnector
        (if (castPlayer.isCastSessionAvailable) castPlayer else localPlayer)?.let {
            setCurrentPlayer(it)
        }
    }

    // Queue manipulation methods.
    /**
     * Plays a specified queue item in the current player.
     *
     * @param itemIndex The index of the item to play.
     */
    fun selectQueueItem(itemIndex: Int, msTime: Long) {
        setCurrentItem(itemIndex, msTime)
    }

    /**
     * Appends `item` to the media queue.
     *
     * @param item The [MediaItem] to append.
     */
    fun addItem(item: MediaItem) {
        castPlayer.addMediaItem(item)
    }

    fun setMediaItems(
        mediaItem: List<MediaItem>,
        itemIndex: Int,
        msTime: Long,
        castMediaItem: List<MediaItem>
    ) {
        println(">>>>>>>>>>>>>>>>> MAEDIA ITEM : $mediaItem")
        mediaQueue = mediaItem as ArrayList<MediaItem>
        castMediaQueue = castMediaItem as ArrayList<MediaItem>
        maybeSetCurrentItemAndNotify(itemIndex)
        if (currentPlayer?.currentTimeline?.windowCount != mediaQueue.size ||
            currentPlayer?.currentTimeline?.windowCount != castMediaQueue.size
        ) {
            // This only happens with the cast player. The receiver app in the cast device clears the
            // timeline when the last item of the timeline has been played to end.
            currentPlayer?.prepare()
            currentPlayer?.setMediaItems(
                if (currentPlayer is CastPlayer) castMediaItem
                else mediaItem,
                itemIndex,
                if (msTime > 0) msTime else C.TIME_UNSET
            )
        } else {
            currentPlayer?.seekTo(itemIndex, if (msTime > 0) msTime else C.TIME_UNSET)
        }
        currentPlayer?.playWhenReady = true
    }

    val mediaQueueSize: Int
        /**
         * Returns the size of the media queue.
         */
        get() = mediaQueue.size

    /**
     * Returns the item at the given index in the media queue.
     *
     * @param position The index of the item.
     * @return The item at the given index in the media queue.
     */
    fun getItem(position: Int): MediaItem {
        return mediaQueue[position]
    }

    /**
     * Removes the item at the given index from the media queue.
     *
     * @param item The item to remove.
     * @return Whether the removal was successful.
     */
    fun removeItem(item: MediaItem): Boolean {
        val itemIndex = mediaQueue.indexOf(item)
        if (itemIndex == -1) {
            return false
        }
        currentPlayer!!.removeMediaItem(itemIndex)
        mediaQueue.removeAt(itemIndex)
        if (itemIndex == currentItemIndex && itemIndex == mediaQueue.size) {
            maybeSetCurrentItemAndNotify(C.INDEX_UNSET)
        } else if (itemIndex < currentItemIndex) {
            maybeSetCurrentItemAndNotify(currentItemIndex - 1)
        }
        return true
    }

    /**
     * Moves an item within the queue.
     *
     * @param item     The item to move.
     * @param newIndex The target index of the item in the queue.
     * @return Whether the item move was successful.
     */
    fun moveItem(item: MediaItem, newIndex: Int): Boolean {
        val fromIndex = mediaQueue.indexOf(item)
        if (fromIndex == -1) {
            return false
        }

        // Player update.
        currentPlayer!!.moveMediaItem(fromIndex, newIndex)
        mediaQueue.add(newIndex, mediaQueue.removeAt(fromIndex))

        // Index update.
        if (fromIndex == currentItemIndex) {
            maybeSetCurrentItemAndNotify(newIndex)
        } else if (currentItemIndex in (fromIndex + 1)..newIndex) {
            maybeSetCurrentItemAndNotify(currentItemIndex - 1)
        } else if (currentItemIndex in newIndex..<fromIndex) {
            maybeSetCurrentItemAndNotify(currentItemIndex + 1)
        }

        return true
    }

    /**
     * Dispatches a given [KeyEvent] to the corresponding view of the current player.
     *
     * @param event The [KeyEvent].
     * @return Whether the event was handled by the target view.
     */
    fun dispatchKeyEvent(event: KeyEvent?): Boolean? {
        return playerView?.dispatchKeyEvent(event!!)
    }

    /**
     * Releases the manager and the players that it holds.
     */
    fun release() {
        /*   mediaSession?.isActive = false
           mediaSession?.release()
           mediaSession = null*/

        currentItemIndex = C.INDEX_UNSET
        mediaQueue.clear()
        castMediaQueue.clear()
        castPlayer.setSessionAvailabilityListener(null)
        castPlayer.release()
        playerView.player = null
        localPlayer?.release()
        mediaSession.release()
        currentPlayer = null
    }

    // Player.Listener implementation.
    override fun onPlaybackStateChanged(playbackState: @Player.State Int) {
        if (currentPlayer is ExoPlayer) {
            mediaItemIndex = currentPlayer?.currentMediaItemIndex ?: this.currentItemIndex
        }
        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>PLay back state")
//        updateCurrentItemIndex()
        listener.onPlaybackStateChanged(playbackState)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: @Player.DiscontinuityReason Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_REMOVE)
            mediaItemIndex = oldPosition.mediaItemIndex
        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>OLD POS : ${oldPosition.mediaItemIndex} NEW:POS: ${newPosition.mediaItemIndex} >> $reason")
        updateCurrentItemIndex()
        listener.onPositionDiscontinuity(oldPosition, newPosition, reason)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: @Player.TimelineChangeReason Int) {
        updateCurrentItemIndex()
        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>TIMELINE")
    }

    override fun onTracksChanged(tracks: Tracks) {
        if (currentPlayer !== localPlayer || tracks === lastSeenTracks) {
            return
        }
        if (tracks.containsType(C.TRACK_TYPE_VIDEO)
            && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO,  /* allowExceedsCapabilities= */true)
        ) {
            listener.onUnsupportedTrack(C.TRACK_TYPE_VIDEO)
        }
        if (tracks.containsType(C.TRACK_TYPE_AUDIO)
            && !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO,  /* allowExceedsCapabilities= */true)
        ) {
            listener.onUnsupportedTrack(C.TRACK_TYPE_AUDIO)
        }
        lastSeenTracks = tracks
    }

    // CastPlayer.SessionAvailabilityListener implementation.
    override fun onCastSessionAvailable() {
        setCurrentPlayer(castPlayer)
        listener.onCastSessionAvailable()
    }

    override fun onCastSessionUnavailable() {
        localPlayer?.let { setCurrentPlayer(it) }
        listener.onCastSessionUnavailable()
    }

    // Internal methods.
    private fun updateCurrentItemIndex() {
        val playbackState = currentPlayer?.playbackState
        maybeSetCurrentItemAndNotify(
            if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED
            ) currentPlayer!!.currentMediaItemIndex
            else C.INDEX_UNSET
        )
        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>MEDIA INDEX ${currentPlayer!!.currentMediaItemIndex}")
    }

    private fun setCurrentPlayer(currentPlayer: Player) {
        if (this.currentPlayer === currentPlayer) {
            return
        }

        playerView.player = currentPlayer
        playerView.controllerHideOnTouch = currentPlayer === localPlayer
        if (currentPlayer === castPlayer) {
            playerView.controllerShowTimeoutMs = 0
            playerView.showController()
            playerView.defaultArtwork = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.ic_cast_connected,  /* theme= */
                null
            )
        } else { // currentPlayer == localPlayer
            playerView.controllerShowTimeoutMs = PlayerControlView.DEFAULT_SHOW_TIMEOUT_MS
            playerView.defaultArtwork = null
        }

        // Player state management.
        var playbackPositionMs = C.TIME_UNSET
        var currentItemIndex = C.INDEX_UNSET
        var playWhenReady = false
        val previousPlayer = this.currentPlayer
        if (previousPlayer != null) {

            // Save state from the previous player.
            val playbackState = previousPlayer.playbackState

            if (playbackState != Player.STATE_ENDED) {
                playbackPositionMs = previousPlayer.currentPosition
                playWhenReady = previousPlayer.playWhenReady
                currentItemIndex = this.currentItemIndex
                mediaItemIndex = this.currentItemIndex

                println(">>>>>>>>>>>>>>>>>>>>>>>>> CURR $currentItemIndex >>>>> ${this.currentItemIndex}")
                /* if (currentItemIndex != this.currentItemIndex) {
                     playbackPositionMs = C.TIME_UNSET
                     currentItemIndex = this.currentItemIndex
                 }*/
            }
            previousPlayer.stop()
            previousPlayer.clearMediaItems()
        }

        this.currentPlayer = currentPlayer

        // Media queue management.
        if (currentPlayer is CastPlayer) {
            println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> CURRENT PLAYER IS CAST >> ${this.currentItemIndex}")
            currentPlayer.setMediaItems(
                castMediaQueue, if (mediaItemIndex != -1) mediaItemIndex
                else currentItemIndex, playbackPositionMs
            )
        } else {
            currentPlayer.clearMediaItems()
            mediaItemIndex = currentItemIndex
            currentPlayer.setMediaItems(mediaQueue, currentItemIndex, playbackPositionMs)
        }
        currentPlayer.playWhenReady = playWhenReady
        currentPlayer.prepare()
    }

    /**
     * Starts playback of the item at the given index.
     *
     * @param itemIndex The index of the item to play.
     */
    private fun setCurrentItem(itemIndex: Int, msTime: Long = 0) {
        maybeSetCurrentItemAndNotify(itemIndex)
        if (currentPlayer?.currentTimeline?.windowCount != mediaQueue.size) {
            // This only happens with the cast player. The receiver app in the cast device clears the
            // timeline when the last item of the timeline has been played to end.
            currentPlayer?.setMediaItems(
                mediaQueue,
                itemIndex,
                if (msTime > 0) msTime else C.TIME_UNSET
            )
        } else {
            currentPlayer?.seekTo(itemIndex, if (msTime > 0) msTime else C.TIME_UNSET)
        }
        currentPlayer?.playWhenReady = true
    }

    private fun maybeSetCurrentItemAndNotify(currentItemIndex: Int) {
        if (this.currentItemIndex != currentItemIndex) {
            val oldIndex = this.currentItemIndex
            this.currentItemIndex = currentItemIndex
            listener.onQueuePositionChanged(oldIndex, currentItemIndex)
        }
    }

    fun play() {
        currentPlayer?.play()
    }

    fun pause() {
        currentPlayer?.pause()
    }

    fun seekTo(mediaIndex: Int, positionMs: Long) {
        try {
            currentPlayer?.seekTo(mediaIndex, positionMs)
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace()
        }
//        currentPlayer?.seekTo(mediaIndex, positionMs)
    }

    fun seekTo(positionMs: Long) {
        currentPlayer?.seekTo(positionMs)
    }

    fun seekFroward() {
        currentPlayer?.seekForward()
    }

    fun seekToPrevious() {
        currentPlayer?.seekToPreviousMediaItem()
    }

    fun isCurrentPlayerPlaying(): Boolean {
        return currentPlayer != null && (currentPlayer?.isPlaying ?: false)
    }

    fun isCurrentPlayerNotPlaying(): Boolean {
        return currentPlayer != null && (currentPlayer?.isPlaying?.not() ?: false)
    }

    fun seekToNext() {
        currentPlayer?.seekToNextMediaItem()
    }

    fun isCurrentPlayerIsNotNull(): Boolean {
        return currentPlayer != null
    }

    fun setPlayWhenReady(value: Boolean) {
        currentPlayer?.playWhenReady = value
    }

    fun releasePlayer() {
        currentPlayer?.release()
    }


    fun isCastSessionAvailable(): Boolean {
        return castPlayer.isCastSessionAvailable
    }

    fun isCastSessionNotAvailable(): Boolean {
        return castPlayer.isCastSessionAvailable.not()
    }

    fun getCurrentPosition(): Long? {
        return currentPlayer?.currentPosition
    }

    fun getCurrentMediaItem(): MediaItem? {
        return currentPlayer?.currentMediaItem
    }

    fun getPlayerMediaMetaData(): MediaMetadata? {
        return currentPlayer?.mediaMetadata
    }

    fun getCurrentPlayer(): Player? = currentPlayer
    override fun onEvents(player: Player, events: Player.Events) {
        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>EVENT >>>> $mediaItemIndex >>>> ${currentPlayer?.currentMediaItemIndex}")
        listener.onEvents(player, events)
        super.onEvents(player, events)
    }

    fun releaseLocalPlayer() {
        mediaQueue.clear()
        localPlayer?.release()
    }

    fun resetMediaItemIndex() {
        mediaItemIndex = -1
    }

    inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            localPlayer?.play()
        }

        override fun onPause() {
            localPlayer?.pause()
        }

        override fun onSkipToNext() {
            // Implement skip to next logic
        }

        override fun onSkipToPrevious() {
            // Implement skip to previous logic
        }

    }

    fun getCurrentMediaItemIndex1(): Int {
        return this.currentItemIndex
    }

}
