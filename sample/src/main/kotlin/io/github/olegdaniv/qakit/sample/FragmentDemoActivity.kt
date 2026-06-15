package io.github.olegdaniv.qakit.sample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * Демо-екран на androidx Fragment'ах — щоб показати автоматичне
 * Fragment lifecycle-логування (таб **Logs**, тег `Lifecycle`).
 *
 * Структура: [DemoFragment] → вкладений [DemoChildFragment],
 * щоб продемонструвати рекурсивне логування child-фрагментів.
 */
class FragmentDemoActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FrameLayout(this).apply {
            id = CONTAINER_ID
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }
        setContentView(container)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(CONTAINER_ID, DemoFragment())
                .commit()
        }
    }

    companion object {
        private const val CONTAINER_ID = 0x0F00_0001
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        fun intent(context: Context): Intent =
            Intent(context, FragmentDemoActivity::class.java)
    }
}

/** Батьківський фрагмент. Додає вкладений [DemoChildFragment] у свій childFragmentManager. */
class DemoFragment : Fragment() {

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        gravity = Gravity.CENTER_HORIZONTAL

        addView(TextView(context).apply {
            text = "DemoFragment\n\nПотруси телефон або повернись назад —\nдивись lifecycle-логи в QA панелі."
            textSize = 16f
            setPadding(48, 96, 48, 48)
            gravity = Gravity.CENTER
        })

        addView(FrameLayout(context).apply { id = CHILD_CONTAINER_ID })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(CHILD_CONTAINER_ID, DemoChildFragment())
                .commit()
        }
    }

    companion object {
        private const val CHILD_CONTAINER_ID = 0x0F00_0002
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}

/** Вкладений фрагмент — логи матимуть host'ом батьківський `DemoFragment`. */
class DemoChildFragment : Fragment() {

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = TextView(requireContext()).apply {
        text = "↳ DemoChildFragment (вкладений)"
        textSize = 14f
        setPadding(48, 24, 48, 24)
    }
}
