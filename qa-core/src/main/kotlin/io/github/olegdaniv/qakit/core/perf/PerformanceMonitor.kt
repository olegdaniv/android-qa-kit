package io.github.olegdaniv.qakit.core.perf

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import androidx.metrics.performance.JankStats
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Оцінка метрики відносно [PerfThresholds].
 */
enum class PerfRating { GOOD, WARNING, BAD }

/**
 * Пороги «здоров'я» метрик продуктивності. Дефолти — за Google Play
 * **Android vitals** (core technical quality). Усі метрики «більше = гірше».
 *
 * @property jankWarnPercent / jankBadPercent  % janky-кадрів (≥25% = slow rendering).
 * @property coldStartWarnMs / coldStartBadMs  cold start (≥5000 мс = excessive startup).
 * @property ttfWarnMs / ttfBadMs              time-to-first-frame (м'якіша рекомендація).
 * @property frameWarnMs / frameBadMs          тривалість кадру (>700 мс = frozen frame).
 * @property heapWarnPercent / heapBadPercent  % зайнятого Java-heap від ліміту.
 */
data class PerfThresholds(
    val jankWarnPercent: Double = 5.0,
    val jankBadPercent: Double = 25.0,
    val coldStartWarnMs: Long = 2_000L,
    val coldStartBadMs: Long = 5_000L,
    val ttfWarnMs: Long = 1_000L,
    val ttfBadMs: Long = 2_500L,
    val frameWarnMs: Double = 16.7,
    val frameBadMs: Double = 700.0,
    val heapWarnPercent: Double = 50.0,
    val heapBadPercent: Double = 80.0,
) {
    /** Оцінка для висхідних порогів (нижче warn = GOOD, ≥ bad = BAD). */
    internal fun rate(value: Double, warn: Double, bad: Double): PerfRating = when {
        value >= bad -> PerfRating.BAD
        value >= warn -> PerfRating.WARNING
        else -> PerfRating.GOOD
    }
}

/**
 * Знімок метрик продуктивності для табу **Perf**.
 *
 * @property coldStartMs       час від старту процесу до [PerformanceMonitor.install] (≈ app init).
 * @property timeToFirstFrameMs час від старту процесу до першого намальованого кадру (null доки не виміряно).
 * @property framesTracked     скільки кадрів обробив JankStats.
 * @property jankyFrames       скільки з них «janky» (не вклались у дедлайн кадру).
 * @property worstFrameMs      найдовший кадр (мс).
 * @property usedHeapMb        зайнято Java-heap зараз.
 * @property maxHeapMb         ліміт Java-heap для процесу.
 * @property nativeHeapMb      зайнято native-heap.
 * @property peakUsedHeapMb    пік Java-heap за час сесії.
 * @property memoryHistoryMb   останні семпли used-heap (для міні-графіка).
 * @property thresholds        пороги, за якими обчислюються рейтинги.
 */
data class PerformanceSnapshot(
    val coldStartMs: Long,
    val timeToFirstFrameMs: Long?,
    val framesTracked: Long,
    val jankyFrames: Long,
    val worstFrameMs: Double,
    val usedHeapMb: Long,
    val maxHeapMb: Long,
    val nativeHeapMb: Long,
    val peakUsedHeapMb: Long,
    val memoryHistoryMb: List<Long>,
    val thresholds: PerfThresholds = PerfThresholds(),
) {
    /** Відсоток janky-кадрів. */
    val jankPercent: Double
        get() = if (framesTracked == 0L) 0.0 else jankyFrames * 100.0 / framesTracked

    /** Відсоток зайнятого Java-heap від ліміту. */
    val heapUsagePercent: Double
        get() = if (maxHeapMb == 0L) 0.0 else usedHeapMb * 100.0 / maxHeapMb

    val coldStartRating: PerfRating
        get() = thresholds.rate(
            coldStartMs.toDouble(),
            thresholds.coldStartWarnMs.toDouble(),
            thresholds.coldStartBadMs.toDouble(),
        )

    /** null доки не виміряно time-to-first-frame. */
    val ttfRating: PerfRating?
        get() = timeToFirstFrameMs?.let {
            thresholds.rate(it.toDouble(), thresholds.ttfWarnMs.toDouble(), thresholds.ttfBadMs.toDouble())
        }

    val jankRating: PerfRating
        get() = thresholds.rate(jankPercent, thresholds.jankWarnPercent, thresholds.jankBadPercent)

    val worstFrameRating: PerfRating
        get() = thresholds.rate(worstFrameMs, thresholds.frameWarnMs, thresholds.frameBadMs)

    val heapRating: PerfRating
        get() = thresholds.rate(heapUsagePercent, thresholds.heapWarnPercent, thresholds.heapBadPercent)
}

/**
 * Збирає метрики продуктивності: jank/кадри (AndroidX [JankStats]),
 * пам'ять у часі та час старту.
 *
 * Реєструється один раз в [QaKit.init] якщо [QaKitConfig.performanceMonitoring] == true.
 *
 * Використання (зазвичай через [QaKit]):
 * ```kotlin
 * PerformanceMonitor.install(application)
 * val snap = PerformanceMonitor.snapshot()
 * PerformanceMonitor.addListener { snap -> updateUi(snap) }
 * ```
 */
object PerformanceMonitor {

    /** Інтервал семплювання пам'яті (мс). */
    var memorySampleIntervalMs: Long = 1_000L

    /** Пороги «здоров'я» метрик. Виставляється з [QaKit.init]. */
    var thresholds: PerfThresholds = PerfThresholds()

    /** Скільки семплів пам'яті тримати для графіка. */
    private const val MEMORY_HISTORY_SIZE = 60

    private val framesTracked = AtomicLong()
    private val jankyFrames = AtomicLong()
    private val worstFrameNanos = AtomicLong()

    @Volatile private var coldStartMs: Long = 0L
    @Volatile private var timeToFirstFrameMs: Long? = null
    @Volatile private var peakUsedHeapMb: Long = 0L

    private val memoryHistory = CopyOnWriteArrayList<Long>()
    private val listeners = CopyOnWriteArrayList<(PerformanceSnapshot) -> Unit>()

    private var application: Application? = null
    private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
    private val jankStatsByActivity = HashMap<Activity, JankStats>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var sampling = false
    private var firstFrameCaptured = false

    /** True якщо монітор зараз активний. */
    val isInstalled: Boolean get() = lifecycleCallbacks != null

    /**
     * Запускає збір метрик. Безпечно викликати повторно — попередній стан скидається.
     */
    fun install(application: Application) {
        uninstall()
        this.application = application

        coldStartMs = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()

        lifecycleCallbacks = createCallbacks().also(application::registerActivityLifecycleCallbacks)

        sampling = true
        mainHandler.post(memorySampler)
    }

    /** Зупиняє збір метрик. Безпечно викликати якщо не запущений. */
    fun uninstall() {
        sampling = false
        mainHandler.removeCallbacks(memorySampler)

        application?.let { app ->
            lifecycleCallbacks?.let(app::unregisterActivityLifecycleCallbacks)
        }
        lifecycleCallbacks = null
        jankStatsByActivity.values.forEach { it.isTrackingEnabled = false }
        jankStatsByActivity.clear()
        application = null
    }

    /** Поточний знімок метрик. */
    fun snapshot(): PerformanceSnapshot {
        val rt = Runtime.getRuntime()
        val usedHeapMb = (rt.totalMemory() - rt.freeMemory()).toMb()
        return PerformanceSnapshot(
            coldStartMs = coldStartMs,
            timeToFirstFrameMs = timeToFirstFrameMs,
            framesTracked = framesTracked.get(),
            jankyFrames = jankyFrames.get(),
            worstFrameMs = worstFrameNanos.get() / 1_000_000.0,
            usedHeapMb = usedHeapMb,
            maxHeapMb = rt.maxMemory().toMb(),
            nativeHeapMb = Debug.getNativeHeapAllocatedSize().toMb(),
            peakUsedHeapMb = peakUsedHeapMb,
            memoryHistoryMb = memoryHistory.toList(),
            thresholds = thresholds,
        )
    }

    /** Скинути лічильники jank (кнопка "Reset" у панелі). */
    fun reset() {
        framesTracked.set(0)
        jankyFrames.set(0)
        worstFrameNanos.set(0)
    }

    fun addListener(listener: (PerformanceSnapshot) -> Unit) = listeners.add(listener)
    fun removeListener(listener: (PerformanceSnapshot) -> Unit) = listeners.remove(listener)

    // --- Internal ---

    private val memorySampler = object : Runnable {
        override fun run() {
            if (!sampling) return
            val rt = Runtime.getRuntime()
            val usedMb = (rt.totalMemory() - rt.freeMemory()).toMb()
            if (usedMb > peakUsedHeapMb) peakUsedHeapMb = usedMb

            if (memoryHistory.size >= MEMORY_HISTORY_SIZE) memoryHistory.removeFirstOrNull()
            memoryHistory.add(usedMb)

            val snap = snapshot()
            listeners.forEach { it(snap) }

            mainHandler.postDelayed(this, memorySampleIntervalMs)
        }
    }

    private fun onFrame(durationNanos: Long, isJank: Boolean) {
        framesTracked.incrementAndGet()
        if (isJank) jankyFrames.incrementAndGet()
        worstFrameNanos.updateAndGet { current -> maxOf(current, durationNanos) }
    }

    /** Перший кадр будь-якої Activity — фіксуємо time-to-first-frame один раз. */
    private fun captureFirstFrame() {
        if (firstFrameCaptured) return
        firstFrameCaptured = true
        Choreographer.getInstance().postFrameCallback {
            timeToFirstFrameMs = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
        }
    }

    private fun Long.toMb(): Long = this / 1024 / 1024

    private fun createCallbacks() = object : Application.ActivityLifecycleCallbacks {

        override fun onActivityResumed(activity: Activity) {
            captureFirstFrame()
            val jankStats = jankStatsByActivity.getOrPut(activity) {
                JankStats.createAndTrack(activity.window) { frameData ->
                    onFrame(frameData.frameDurationUiNanos, frameData.isJank)
                }
            }
            jankStats.isTrackingEnabled = true
        }

        override fun onActivityPaused(activity: Activity) {
            jankStatsByActivity[activity]?.isTrackingEnabled = false
        }

        override fun onActivityDestroyed(activity: Activity) {
            jankStatsByActivity.remove(activity)?.isTrackingEnabled = false
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
