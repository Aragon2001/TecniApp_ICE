package com.Arasoftsolutions.tecniapp_ice.ui.onboarding

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.Arasoftsolutions.tecniapp_ice.databinding.ItemOnboardingPageBinding

class OnboardingAdapter(
    private val pages: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    inner class ViewHolder(
        private val binding: ItemOnboardingPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: OnboardingPage) {
            val ctx = binding.root.context

            binding.imageIllustration.setImageResource(page.illustrationRes)
            binding.textTitle.setText(page.titleRes)
            binding.textDescription.setText(page.descriptionRes)

            val accent = ContextCompat.getColor(ctx, page.accentColorRes)

            // Círculo de fondo: color de acento al ~18% de opacidad
            val bgColor = Color.argb(
                46,
                Color.red(accent),
                Color.green(accent),
                Color.blue(accent)
            )
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }.also { binding.illustrationBg.background = it }

            // Ícono con el color de acento completo
            binding.imageIllustration.imageTintList = ColorStateList.valueOf(accent)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size
}
