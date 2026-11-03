package com.rosebank.pitchpulse

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.rosebank.pitchpulse.databinding.ActivityOnboardingBinding
import com.rosebank.pitchpulse.databinding.ItemOnboardingBinding

/** Three-slide first-run education. Skip and Get Started both mark onboarding done. */
class OnboardingActivity : BaseActivity() {
    private val pages = listOf(
        Triple(R.string.onb_1_title, R.string.onb_1_body, R.drawable.ic_pulse_logo),
        Triple(R.string.onb_2_title, R.string.onb_2_body, R.drawable.ic_pulse_logo),
        Triple(R.string.onb_3_title, R.string.onb_3_body, R.drawable.ic_pulse_logo)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.pager.adapter = PageAdapter(pages)
        val dots = pages.indices.map {
            View(this).apply {
                val size = (8 * resources.displayMetrics.density).toInt()
                layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(getColor(R.color.muted))
                }
            }.also { binding.dots.addView(it) }
        }
        fun paint(index: Int) {
            dots.forEachIndexed { i, view ->
                (view.background as GradientDrawable).setColor(
                    getColor(if (i == index) R.color.cyan else R.color.muted)
                )
            }
            binding.cta.text = getString(if (index == pages.lastIndex) R.string.get_started else R.string.next)
        }
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = paint(position)
        })
        paint(0)
        binding.skip.setOnClickListener { finishOnboarding() }
        binding.cta.setOnClickListener {
            val next = binding.pager.currentItem + 1
            if (next < pages.size) binding.pager.currentItem = next else finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        Prefs.setOnboarded(this)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private class PageAdapter(val pages: List<Triple<Int, Int, Int>>) :
        RecyclerView.Adapter<PageAdapter.Holder>() {
        class Holder(val binding: ItemOnboardingBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemOnboardingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(binding)
        }

        override fun getItemCount() = pages.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val (title, body, art) = pages[position]
            holder.binding.title.setText(title)
            holder.binding.body.setText(body)
            holder.binding.art.setImageResource(art)
        }
    }
}
