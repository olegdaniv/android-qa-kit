package io.github.olegdaniv.qakit.core.lifecycle

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.github.olegdaniv.qakit.core.logger.QaLogger

/**
 * Автоматично логує lifecycle-події Activity (і, опційно, Fragment) через [QaLogger].
 *
 * Реєструється один раз в [QaKit.init] якщо [QaKitConfig.lifecycleLogging] == true.
 * Події видно в табі **Logs** QA панелі з тегом [TAG].
 *
 * Fragment-події логуються лише для [FragmentActivity] (androidx) — для них
 * автоматично (рекурсивно, включно з child-фрагментами) підписується
 * [FragmentManager.FragmentLifecycleCallbacks].
 *
 * Використання (зазвичай не потрібне напряму — робить [QaKit]):
 * ```kotlin
 * ActivityLifecycleLogger.install(application, logFragments = true)
 * ActivityLifecycleLogger.uninstall(application)
 * ```
 */
object ActivityLifecycleLogger {

    const val TAG = "Lifecycle"

    private var callbacks: Application.ActivityLifecycleCallbacks? = null

    /** True якщо логер зараз зареєстрований. */
    val isInstalled: Boolean get() = callbacks != null

    /**
     * Починає логувати lifecycle-події.
     * Безпечно викликати повторно — попередній колбек знімається.
     *
     * @param logFragments чи логувати також Fragment-події для [FragmentActivity].
     */
    fun install(application: Application, logFragments: Boolean = true) {
        uninstall(application)
        callbacks = LoggingCallbacks(logFragments)
            .also(application::registerActivityLifecycleCallbacks)
    }

    /**
     * Припиняє логування. Безпечно викликати якщо [install] не викликався.
     */
    fun uninstall(application: Application) {
        callbacks?.let(application::unregisterActivityLifecycleCallbacks)
        callbacks = null
    }

    private class LoggingCallbacks(
        private val logFragments: Boolean,
    ) : Application.ActivityLifecycleCallbacks {

        private val fragmentCallbacks = FragmentLogger()

        private fun log(activity: Activity, event: String) =
            QaLogger.d(TAG, "${activity.localName} → $event")

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            val restored = if (savedInstanceState != null) " (restored)" else ""
            QaLogger.d(TAG, "${activity.localName} → onCreate$restored")

            if (logFragments && activity is FragmentActivity) {
                activity.supportFragmentManager
                    .registerFragmentLifecycleCallbacks(fragmentCallbacks, /* recursive = */ true)
            }
        }

        override fun onActivityStarted(activity: Activity) = log(activity, "onStart")
        override fun onActivityResumed(activity: Activity) = log(activity, "onResume")
        override fun onActivityPaused(activity: Activity) = log(activity, "onPause")
        override fun onActivityStopped(activity: Activity) = log(activity, "onStop")
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
            log(activity, "onSaveInstanceState")

        override fun onActivityDestroyed(activity: Activity) {
            if (logFragments && activity is FragmentActivity) {
                activity.supportFragmentManager
                    .unregisterFragmentLifecycleCallbacks(fragmentCallbacks)
            }
            log(activity, "onDestroy")
        }

        private val Activity.localName: String
            get() = this::class.java.simpleName.ifEmpty { this::class.java.name }
    }

    private class FragmentLogger : FragmentManager.FragmentLifecycleCallbacks() {

        private fun log(f: Fragment, event: String) =
            QaLogger.d(TAG, "${f.hostName}/${f.localName} → $event")

        override fun onFragmentAttached(fm: FragmentManager, f: Fragment, context: Context) =
            log(f, "onAttach")

        override fun onFragmentCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
            val restored = if (savedInstanceState != null) " (restored)" else ""
            log(f, "onCreate$restored")
        }

        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: android.view.View,
            savedInstanceState: Bundle?,
        ) = log(f, "onViewCreated")

        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) = log(f, "onStart")
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) = log(f, "onResume")
        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) = log(f, "onPause")
        override fun onFragmentStopped(fm: FragmentManager, f: Fragment) = log(f, "onStop")
        override fun onFragmentSaveInstanceState(fm: FragmentManager, f: Fragment, outState: Bundle) =
            log(f, "onSaveInstanceState")

        override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) =
            log(f, "onDestroyView")

        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) = log(f, "onDestroy")
        override fun onFragmentDetached(fm: FragmentManager, f: Fragment) = log(f, "onDetach")

        private val Fragment.localName: String
            get() = this::class.java.simpleName.ifEmpty { this::class.java.name }

        /** Ім'я host-Activity (або батьківського фрагмента для child-фрагментів). */
        private val Fragment.hostName: String
            get() = parentFragment?.let { it::class.java.simpleName }
                ?: activity?.let { it::class.java.simpleName }
                ?: "?"
    }
}
