package com.example.ijkradio.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ijkradio.data.Station
import com.example.ijkradio.ui.PlaybackState
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

/**
 * ExoPlayer 播放器管理器
 * 使用 ExoPlayer 作为播放器引擎，支持 HLS (m3u8) 和普通流
 */
class ExoPlayerManager(private val context: Context) : IPlayerManager {

    companion object {
        private const val TAG = "ExoPlayerManager"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }

    // ExoPlayer 实例
    private var exoPlayer: ExoPlayer? = null

    // 当前播放的电台
    private var currentStation: Station? = null

    // 播放状态
    private val _playbackState = MutableLiveData<PlaybackState>(PlaybackState.Stopped)
    override val playbackState: LiveData<PlaybackState> = _playbackState

    // 音量
    private var currentVolume = 1.0f

    // 硬解码开关
    private var hardwareDecodeEnabled = true

    // 是否已初始化
    private var isInitialized = false

    override fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "Player already initialized")
            return
        }

        try {
            buildPlayer()
            isInitialized = true
            Log.d(TAG, "ExoPlayer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ExoPlayer", e)
            _playbackState.postValue(PlaybackState.Error("ExoPlayer 初始化失败: ${e.message}"))
        }
    }

    /**
     * 构建 ExoPlayer 实例
     */
    private fun buildPlayer() {
        val renderersFactory = DefaultRenderersFactory(context)

        exoPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context))
            .build()

        exoPlayer?.addListener(playerListener)
    }

    /**
     * 重建播放器
     */
    private fun rebuildPlayer() {
        release()
        buildPlayer()
    }

    override fun playStation(station: Station) {
        Log.d(TAG, "Playing station: ${station.name}, URL: ${station.url}")
        currentStation = station
        _playbackState.postValue(PlaybackState.Buffering)

        try {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)

            val mediaSource = createMediaSource(station.url, dataSourceFactory)

            exoPlayer?.apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing station", e)
            _playbackState.postValue(PlaybackState.Error("播放失败: ${e.message}"))
        }
    }

    /**
     * 根据 URL 类型创建对应的 MediaSource
     */
    private fun createMediaSource(url: String, dataSourceFactory: DefaultHttpDataSource.Factory): MediaSource {
        val mediaItem = MediaItem.fromUri(url)
        return when {
            url.endsWith(".m3u8", ignoreCase = true) -> {
                // HLS 流
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            else -> {
                // 普通流（MP3 等）
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
        }
    }

    /**
     * 获取对应的 Referer
     */
    private fun getReferer(url: String): String {
        return when {
            url.contains("cnr.cn") -> "http://ngcdn001.cnr.cn/"
            url.contains("cri.cn") || url.contains("sk.cri.cn") -> "https://sk.cri.cn/"
            url.contains("hnradio.com") -> "http://a.live.hnradio.com/"
            url.contains("asiafm.hk") || url.contains("asiafm.net") || url.contains("goldfm.cn") -> "http://asiafm.hk/"
            url.contains("qingting.fm") -> "https://www.qingting.fm/"
            else -> ""
        }
    }

    override fun pause() {
        try {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    player.playWhenReady = false
                    _playbackState.postValue(PlaybackState.Paused)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback", e)
        }
    }

    override fun resume() {
        try {
            exoPlayer?.let { player ->
                player.playWhenReady = true
                currentStation?.let { station ->
                    _playbackState.postValue(PlaybackState.Playing(station.name))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
        }
    }

    override fun stop() {
        try {
            exoPlayer?.stop()
            _playbackState.postValue(PlaybackState.Stopped)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }

    override fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        try {
            exoPlayer?.volume = currentVolume
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }

    override fun getVolume(): Float = currentVolume

    override fun setHardwareDecode(enabled: Boolean) {
        hardwareDecodeEnabled = enabled
        if (isInitialized && (exoPlayer?.isPlaying == true || currentStation != null)) {
            // 需要重建播放器以应用新的硬解码设置
            Log.d(TAG, "Rebuilding player with hardware decode: $enabled")
            rebuildPlayer()
            // 重新播放当前电台
            currentStation?.let { playStation(it) }
        }
    }

    override fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying == true
    }

    override fun getCurrentStation(): Station? = currentStation

    override fun release() {
        try {
            exoPlayer?.release()
            exoPlayer = null
            isInitialized = false
            currentStation = null
            _playbackState.postValue(PlaybackState.Stopped)
            Log.d(TAG, "ExoPlayer released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ExoPlayer", e)
        }
    }

    /**
     * ExoPlayer 监听器
     */
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "Buffering")
                    _playbackState.postValue(PlaybackState.Buffering)
                }
                Player.STATE_READY -> {
                    Log.d(TAG, "Ready to play")
                    currentStation?.let { station ->
                        _playbackState.postValue(PlaybackState.Playing(station.name))
                    }
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Playback ended")
                    _playbackState.postValue(PlaybackState.Stopped)
                }
                Player.STATE_IDLE -> {
                    Log.d(TAG, "Player idle")
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val errorMessage = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                    "网络连接失败，请检查网络"
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    "网络连接超时"
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                    "流格式解析错误"
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                    "HLS 清单解析错误"
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                    "解码器初始化失败"
                PlaybackException.ERROR_CODE_DECODING_FAILED ->
                    "解码失败"
                else ->
                    "播放错误: ${error.message ?: "未知错误"}"
            }
            Log.e(TAG, "Playback error: $errorMessage")
            _playbackState.postValue(PlaybackState.Error(errorMessage))
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "Is playing changed: $isPlaying")
        }
    }
}
