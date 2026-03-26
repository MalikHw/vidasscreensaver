package com.malikhw.vidasscrsvr

import android.net.Uri
import android.service.dreams.DreamService
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class VideoDreamService : DreamService() {

    private var player: ExoPlayer? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false

        val prefs = getSharedPreferences("vid_scrsvr_prefs", MODE_PRIVATE)
        val uriString = prefs.getString("video_uri", null)
        val soundOn = prefs.getBoolean("sound_on", false)
        val volume = prefs.getFloat("volume", 0.5f)
        val scaleFill = prefs.getBoolean("scale_fill", true)

        if (uriString == null) {
            finish()
            return
        }

        val surface = SurfaceView(this)
        surface.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        setContentView(surface)

        val uri = Uri.parse(uriString)

        player = ExoPlayer.Builder(this).build().also { p ->
            p.setMediaItem(MediaItem.fromUri(uri))
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.volume = if (soundOn) volume else 0f
            p.setVideoSurfaceView(surface)
            p.prepare()
            p.play()
        }

        if (scaleFill) {
            surface.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {}
                override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
                    if (w > 0 && h > 0) {
                        val dm = resources.displayMetrics
                        val scaleX = dm.widthPixels.toFloat() / w
                        val scaleY = dm.heightPixels.toFloat() / h
                        val scale = maxOf(scaleX, scaleY)
                        surface.scaleX = scale
                        surface.scaleY = scale
                    }
                }
                override fun surfaceDestroyed(holder: SurfaceHolder) {}
            })
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        player?.play()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        player?.pause()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        player?.release()
        player = null
    }
}
