package com.example.ijkradio.player

import androidx.lifecycle.LiveData
import com.example.ijkradio.data.Station
import com.example.ijkradio.ui.PlaybackState

/**
 * 播放器统一接口
 * 定义播放器的标准操作，供 IjkPlayerManager 和 ExoPlayerManager 实现
 */
interface IPlayerManager {
    /**
     * 当前播放状态
     */
    val playbackState: LiveData<PlaybackState>

    /**
     * 初始化播放器
     */
    fun initialize()

    /**
     * 播放电台
     * @param station 要播放的电台
     */
    fun playStation(station: Station)

    /**
     * 暂停播放
     */
    fun pause()

    /**
     * 恢复播放
     */
    fun resume()

    /**
     * 停止播放
     */
    fun stop()

    /**
     * 设置音量
     * @param volume 音量值 (0.0 - 1.0)
     */
    fun setVolume(volume: Float)

    /**
     * 获取当前音量
     * @return 当前音量值
     */
    fun getVolume(): Float

    /**
     * 设置硬解码开关
     * @param enabled 是否启用硬解码
     */
    fun setHardwareDecode(enabled: Boolean)

    /**
     * 是否正在播放
     * @return 是否正在播放
     */
    fun isPlaying(): Boolean

    /**
     * 获取当前播放的电台
     * @return 当前电台，null 表示未播放
     */
    fun getCurrentStation(): Station?

    /**
     * 释放播放器资源
     */
    fun release()
}
