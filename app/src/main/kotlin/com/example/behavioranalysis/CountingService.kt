package com.example.behavioranalysis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.behavioranalysis.data.database.AppDatabase
import com.example.behavioranalysis.data.entity.BehaviorRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 行動計測用 Foreground Service。
 *
 * - アプリがバックグラウンドに移行してもタイマーと集計を継続する
 * - 通知に「−」「＋」ボタンを表示し、画面外からのカウント操作を可能にする
 * - Fragment がフォアグラウンドに戻ったときは Binder 経由で状態を同期する
 *
 * ※ 音量ボタンによるバックグラウンド操作は Android 10+ の制限で不可。
 *   フォアグラウンド時は BehaviorDetailActivity.onKeyDown → Fragment.handleKeyDown
 *   → increment/decrement の経路で引き続き動作する。
 */
class CountingService : Service() {

    // ── 定数 ──────────────────────────────────────────────────────────

    companion object {
        const val CHANNEL_ID = "muninn_counting"
        const val NOTIFICATION_ID = 1001
        const val ACTION_INCREMENT = "com.example.behavioranalysis.INCREMENT"
        const val ACTION_DECREMENT = "com.example.behavioranalysis.DECREMENT"
    }

    // ── 計測状態（Service が保持する Single source of truth） ───────────

    var behaviorId: Long = -1
        private set
    var behaviorName: String = ""
        private set
    var selectedIntervalSeconds: Int = 0
        private set
    var currentCount: Int = 0
        private set
    var currentIntervalNum: Int = 0
        private set
    val intervalCounts: MutableList<Int> = mutableListOf()
    var countInCurrentInterval: Int = 0
        private set
    var isRunning: Boolean = false
        private set
    var remainingMs: Long = 0L
        private set

    // ── コールバック（Fragment が在前時のみ非 null） ─────────────────────

    interface CountingCallback {
        fun onStateChanged()
        fun onTimerTick(remainingMs: Long)
        fun onIntervalComplete(intervalNum: Int)
    }

    var callback: CountingCallback? = null

    // ── Binder ──────────────────────────────────────────────────────

    inner class CountingBinder : Binder() {
        fun getService(): CountingService = this@CountingService
    }

    private val binder = CountingBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    // ── Coroutine ──────────────────────────────────────────────────

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var countDownTimer: CountDownTimer? = null

    // ── ライフサイクル ─────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * 通知ボタンからの操作を受け取る。
     * Fragment からは Binder 経由で直接メソッドを呼ぶので不要だが、
     * PendingIntent（通知ボタン）は startService 経由でしか呼べないため残す。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INCREMENT -> increment()
            ACTION_DECREMENT -> decrement()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        serviceJob.cancel()
    }

    // ── 公開 API（Fragment / BroadcastReceiver から呼ぶ） ──────────────

    /** 計測セッションを開始し、フォアグラウンド通知を立ち上げる */
    fun startCounting(
        behaviorId: Long,
        behaviorName: String,
        intervalSeconds: Int
    ) {
        this.behaviorId = behaviorId
        this.behaviorName = behaviorName
        this.selectedIntervalSeconds = intervalSeconds
        currentCount = 0
        currentIntervalNum = 0
        intervalCounts.clear()
        countInCurrentInterval = 0
        remainingMs = intervalSeconds * 1000L
        isRunning = true

        if (intervalSeconds > 0) startIntervalTimer()

        startForeground(NOTIFICATION_ID, buildNotification())
        callback?.onStateChanged()
    }

    /** タイマーを停止し通知を除去する。カウントデータは保持する（保存用に残す）。*/
    fun stopCounting() {
        isRunning = false
        countDownTimer?.cancel()
        countDownTimer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        callback?.onStateChanged()
    }

    /** 状態をリセットし、クライアントがいなければ Service も終了する */
    fun resetState() {
        currentCount = 0
        currentIntervalNum = 0
        intervalCounts.clear()
        countInCurrentInterval = 0
        remainingMs = 0L
        callback?.onStateChanged()
        stopSelf()  // バインドが残っていれば破棄されない
    }

    /** DB 保存 → stopCounting → resetState → コールバック通知 */
    fun saveAndReset(database: AppDatabase, onComplete: () -> Unit) {
        val notes: String? = if (selectedIntervalSeconds > 0 && intervalCounts.isNotEmpty()) {
            val all = intervalCounts.toMutableList()
            if (countInCurrentInterval > 0) all.add(countInCurrentInterval)
            IntervalNotesUtil.encode(all)
        } else null

        val record = BehaviorRecord(
            behaviorId = behaviorId,
            count = currentCount,
            notes = notes
        )

        serviceScope.launch {
            database.behaviorRecordDao().insert(record)
            withContext(Dispatchers.Main) {
                stopCounting()
                resetState()
                onComplete()
            }
        }
    }

    fun increment() {
        if (!isRunning) return
        currentCount++
        if (selectedIntervalSeconds > 0) countInCurrentInterval++
        updateNotification()
        callback?.onStateChanged()
    }

    fun decrement() {
        if (!isRunning || currentCount <= 0) return
        currentCount--
        if (selectedIntervalSeconds > 0 && countInCurrentInterval > 0) countInCurrentInterval--
        updateNotification()
        callback?.onStateChanged()
    }

    // ── タイマー ──────────────────────────────────────────────────────

    private fun startIntervalTimer() {
        countDownTimer?.cancel()
        remainingMs = selectedIntervalSeconds * 1000L

        countDownTimer = object : CountDownTimer(remainingMs, 100) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                updateNotification()
                callback?.onTimerTick(millisUntilFinished)
            }

            override fun onFinish() {
                intervalCounts.add(countInCurrentInterval)
                currentIntervalNum++
                countInCurrentInterval = 0
                callback?.onIntervalComplete(currentIntervalNum)
                updateNotification()
                startIntervalTimer()    // 次のインターバルへ
            }
        }.start()
    }

    // ── 通知 ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "計測中",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Muninn 行動計測中の通知"
            setSound(null, null)
            enableVibration(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        // タップでアプリを前面に戻す
        val openIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName)
                ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val decrementIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CountingService::class.java).apply { action = ACTION_DECREMENT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val incrementIntent = PendingIntent.getService(
            this, 2,
            Intent(this, CountingService::class.java).apply { action = ACTION_INCREMENT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timerText = if (selectedIntervalSeconds > 0) {
            "  残り %.1f 秒".format(remainingMs / 1000.0)
        } else ""
        val intervalText = if (currentIntervalNum > 0) "  インターバル ${currentIntervalNum}" else ""
        val bodyText = "${currentCount}回$intervalText$timerText"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("計測中: $behaviorName")
            .setContentText(bodyText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(android.R.drawable.btn_minus, "−", decrementIntent)
            .addAction(android.R.drawable.btn_plus, "+", incrementIntent)
            .build()
    }

    private fun updateNotification() {
        if (!isRunning) return
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification())
    }
}
