package com.malikhw.vidasscrsvr

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton

object AboutDialog {

    private val LINKS = listOf(
        "Website" to "https://malikhw.github.io",
        "YouTube" to "https://youtube.com/@MalikHw47",
        "GitHub" to "https://github.com/MalikHw",
        "Twitch" to "https://twitch.tv/MalikHw47",
        "Discord" to "https://discord.gg/G9bZ92eg2n",
        "Get me a Gift" to "https://throne.com/MalikHw47",
        "Ko-fi" to "https://ko-fi.com/MalikHw47"
    )

    fun show(activity: Activity) {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_about)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val pfp = dialog.findViewById<ImageView>(R.id.imgAboutPfp)
        pfp.clipToOutline = true
        pfp.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }

        val linksContainer = dialog.findViewById<LinearLayout>(R.id.aboutLinksContainer)
        val inflater = LayoutInflater.from(activity)
        LINKS.chunked(3).forEach { row ->
            val rowLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12 }
            }
            row.forEach { (label, url) ->
                val btn = inflater.inflate(R.layout.item_about_link, rowLayout, false) as MaterialButton
                btn.text = label
                btn.setOnClickListener {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                rowLayout.addView(btn)
            }
            linksContainer.addView(rowLayout)
        }

        dialog.findViewById<MaterialButton>(R.id.btnAboutClose).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
