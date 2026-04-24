package com.example.ijkradio

import android.content.Context
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.multidex.MultiDex
import com.example.ijkradio.data.Station
import com.example.ijkradio.data.StationStorage
import com.example.ijkradio.player.IPlayerManager
import com.example.ijkradio.player.ExoPlayerManager
import com.example.ijkradio.ui.PlaybackState
import com.example.ijkradio.ui.StationAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider

/**
 * 主界面Activity
 * 管理电台列表、播放器控制和UI交互
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // UI组件
    private lateinit var recyclerView: RecyclerView
    private lateinit var playPauseButton: FloatingActionButton
    private lateinit var settingsButton: ImageButton
    private lateinit var statusTextView: TextView
    private lateinit var emptyView: TextView
    private lateinit var volumeSlider: Slider
    private lateinit var volumeIcon: ImageView

    // 适配器
    private lateinit var stationAdapter: StationAdapter

    // 数据和播放器
    private lateinit var stationStorage: StationStorage
    private lateinit var playerManager: IPlayerManager

    // 电台列表
    private var stations: MutableList<Station> = mutableListOf()
    private var selectedStation: Station? = null
    private var autoPlayEnabled = true   // 遥控器移动焦点时自动播放
    private var autoPlayLastStationEnabled = true   // 自动播放上一次播放的电台

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate: Initializing activity")

        // 初始化组件
        initStorage()
        initViews()
        initPlayer()
        initRecyclerView()
        setupListeners()

        // 加载电台列表
        loadStations()

        // 恢复上次播放
        restoreLastPlayed()
    }

    /**
     * 初始化UI组件
     */
    private fun initViews() {
        recyclerView = findViewById(R.id.stations_recycler_view)
        playPauseButton = findViewById(R.id.play_pause_button)
        settingsButton = findViewById(R.id.settings_button)
        statusTextView = findViewById(R.id.status_text_view)
        emptyView = findViewById(R.id.empty_view)
    }

    /**
     * 初始化存储
     */
    private fun initStorage() {
        stationStorage = StationStorage(this)
    }

    /**
     * 初始化播放器
     */
    private fun initPlayer() {
        initPlayerManager()
    }

    /**
     * 初始化播放器管理器
     * 只使用 ExoPlayer
     */
    private fun initPlayerManager() {
        playerManager = ExoPlayerManager(this)
        playerManager.initialize()

        // 设置保存的音量
        playerManager.setVolume(stationStorage.getVolume())

        // 监听播放器状态
        playerManager.playbackState.observe(this) {
            updatePlaybackUI(it)
        }
    }

    /**
     * 初始化RecyclerView
     */
    private fun initRecyclerView() {
        stationAdapter = StationAdapter(
            onStationClick = { station -> onStationClicked(station) },
            onDeleteClick = { station -> showDeleteDialog(station) }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = stationAdapter
            setHasFixedSize(true)
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }

        // 添加左滑删除功能
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val station = stationAdapter.getItemAt(position)
                if (station != null) {
                    showDeleteDialog(station)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 播放/暂停按钮
        playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        // 添加电台按钮
        findViewById<ImageButton>(R.id.add_station_button).setOnClickListener {
            showAddStationDialog()
        }

        // 设置按钮
        settingsButton.setOnClickListener {
            showSettingsDialog()
        }
    }

    /**
     * 加载电台列表
     */
    private fun loadStations() {
        stations = stationStorage.getStations().toMutableList()
        stationAdapter.submitList(stations.toList())
        updateEmptyView()
    }

    /**
     * 恢复上次播放
     */
    private fun restoreLastPlayed() {
        val lastPlayed = stationStorage.getLastPlayed()
        if (lastPlayed != null) {
            selectedStation = lastPlayed
            stationAdapter.setSelectedStation(lastPlayed)
            // 如果启用了自动播放上一次电台，则自动播放
            if (autoPlayLastStationEnabled) {
                playerManager.playStation(lastPlayed)
            }
        }
    }

    /**
     * 电台点击事件
     */
    private fun onStationClicked(station: Station) {
        Log.d(TAG, "Station clicked: ${station.name}")
        selectedStation = station
        stationAdapter.setSelectedStation(station)
        stationStorage.saveLastPlayed(station)

        // 如果当前正在播放其他电台，切换到新电台
        val currentStation = playerManager.getCurrentStation()
        if (currentStation?.id != station.id) {
            playerManager.playStation(station)
        }
    }

    /**
     * 切换播放/暂停
     */
    private fun togglePlayPause() {
        val currentStation = selectedStation ?: return

        when {
            playerManager.isPlaying() -> {
                playerManager.pause()
            }
            playerManager.getCurrentStation()?.id == currentStation.id -> {
                playerManager.resume()
            }
            else -> {
                playerManager.playStation(currentStation)
            }
        }
    }

    /**
     * 更新播放UI
     */
    private fun updatePlaybackUI(state: PlaybackState) {
        when (state) {
            is PlaybackState.Stopped -> {
                statusTextView.text = getString(R.string.status_stopped)
                playPauseButton.setImageResource(R.drawable.ic_play)
                stationAdapter.setPlayingStation(null)
            }
            is PlaybackState.Buffering -> {
                statusTextView.text = getString(R.string.status_buffering)
                playPauseButton.setImageResource(R.drawable.ic_pause)
            }
            is PlaybackState.Playing -> {
                statusTextView.text = getString(R.string.status_playing, state.stationName)
                playPauseButton.setImageResource(R.drawable.ic_pause)
                val station = stations.find { it.name == state.stationName }
                stationAdapter.setPlayingStation(station)
            }
            is PlaybackState.Paused -> {
                statusTextView.text = getString(R.string.status_paused)
                playPauseButton.setImageResource(R.drawable.ic_play)
            }
            is PlaybackState.Error -> {
                statusTextView.text = getString(R.string.status_error, state.message)
                playPauseButton.setImageResource(R.drawable.ic_play)
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 显示添加电台对话框
     */
    private fun showAddStationDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_station, null)

        val nameInput = dialogView.findViewById<EditText>(R.id.station_name_input)
        val urlInput = dialogView.findViewById<EditText>(R.id.station_url_input)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.station_description_input)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_station_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_add) { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()

                if (name.isNotEmpty() && url.isNotEmpty()) {
                    val station = Station(
                        name = name,
                        url = if (url.startsWith("http")) url else "http://$url",
                        description = description
                    )
                    addStation(station)
                } else {
                    Toast.makeText(this, R.string.error_invalid_input, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteDialog(station: Station) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, station.name))
            .setPositiveButton(R.string.dialog_delete) { _, _ ->
                deleteStation(station)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * 添加电台
     */
    private fun addStation(station: Station) {
        if (station.isValid()) {
            stationStorage.addStation(station)
            loadStations()
            Toast.makeText(this, R.string.station_added, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.error_invalid_station, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 删除电台
     */
    private fun deleteStation(station: Station) {
        // 如果删除的是当前播放的电台，停止播放
        if (playerManager.getCurrentStation()?.id == station.id) {
            playerManager.stop()
        }

        // 如果删除的是选中的电台，清除选中
        if (selectedStation?.id == station.id) {
            selectedStation = null
        }

        stationStorage.removeStation(station)
        loadStations()
        Toast.makeText(this, R.string.station_deleted, Toast.LENGTH_SHORT).show()
    }

    /**
     * 更新空状态视图
     */
    private fun updateEmptyView() {
        if (stations.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    // ==================== 生命周期管理 ====================

    override fun onPause() {
        super.onPause()
        // 保存当前状态
        playerManager.getCurrentStation()?.let {
            stationStorage.saveLastPlayed(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放播放器资源
        if (::playerManager.isInitialized) {
            playerManager.release()
        }
    }

    /**
     * 显示设置对话框
     */
    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_settings, null)

        volumeSlider = dialogView.findViewById<Slider>(R.id.volume_slider)
        volumeIcon = dialogView.findViewById<ImageView>(R.id.volume_icon)
        val radioHardware = dialogView.findViewById<RadioButton>(R.id.radio_hardware)
        val radioSoftware = dialogView.findViewById<RadioButton>(R.id.radio_software)
        val autoPlaySwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.auto_play_switch)
        val autoPlayLastStationSwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.auto_play_last_station_switch)

        // 初始化音量滑块
        volumeSlider.value = stationStorage.getVolume()
        updateVolumeIcon(stationStorage.getVolume())

        // 初始化解码方式，默认打开软解码
        radioSoftware.isChecked = true
        playerManager.setHardwareDecode(false)

        // 初始化自动播放开关
        autoPlaySwitch.isChecked = autoPlayEnabled

        // 初始化自动播放上一次电台开关
        autoPlayLastStationSwitch.isChecked = autoPlayLastStationEnabled

        // 音量滑块监听器
        volumeSlider.addOnChangeListener { slider: Slider, value: Float, fromUser: Boolean ->
            if (fromUser) {
                playerManager.setVolume(value)
                stationStorage.saveVolume(value)
                updateVolumeIcon(value)
            }
        }

        // 自动播放开关监听器
        autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            autoPlayEnabled = isChecked
        }

        // 自动播放上一次电台开关监听器
        autoPlayLastStationSwitch.setOnCheckedChangeListener { _, isChecked ->
            autoPlayLastStationEnabled = isChecked
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("设置")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                // 保存解码方式设置
                val useHardwareDecode = radioHardware.isChecked
                playerManager.setHardwareDecode(useHardwareDecode)
            }
            .setNegativeButton("取消", null)
            .create()

        // 设置按钮颜色
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(resources.getColor(R.color.colorPrimary))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(resources.getColor(R.color.colorPrimary))
        }

        dialog.show()
    }

    /**
     * 更新音量图标
     */
    private fun updateVolumeIcon(volume: Float) {
        val iconRes = when {
            volume <= 0f -> R.drawable.ic_volume_off
            volume < 0.5f -> R.drawable.ic_volume_down
            else -> R.drawable.ic_volume_up
        }
        volumeIcon.setImageResource(iconRes)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                moveSelection(-1)
                return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveSelection(1)
                return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                selectedStation?.let {
                    if (playerManager.isPlaying() && playerManager.getCurrentStation()?.id == it.id) {
                        playerManager.pause()
                    } else {
                        playStationAndUpdateUI(it)
                    }
                }
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun moveSelection(delta: Int) {
        val count = stationAdapter.itemCount
        if (count == 0) return

        // 获取当前选中位置，若无选中则根据方向决定起始位置
        val currentPos = if (selectedStation == null) {
            if (delta > 0) -1 else count  // 使下方模运算得到正确边界
        } else {
            stations.indexOfFirst { it.id == selectedStation?.id }.coerceAtLeast(0)
        }

        // 循环计算新位置
        val newPos = ((currentPos + delta) % count + count) % count
        val station = stationAdapter.getItemAt(newPos)
        stationAdapter.setSelectedStation(station)
        recyclerView.smoothScrollToPosition(newPos)
        if (autoPlayEnabled && station != null) {
            playStationAndUpdateUI(station)
        }
    }

    private fun getSelectedPosition(): Int {
        val selected = stationAdapter.getSelectedStation() ?: return 0
        return stations.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
    }

    private fun playStationAndUpdateUI(station: Station) {
        onStationClicked(station)
    }

    override fun onBackPressed() {
        // 最小化应用而不是退出
        moveTaskToBack(true)
        super.onBackPressed()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
