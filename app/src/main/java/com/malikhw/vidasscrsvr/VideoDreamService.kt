package com.malikhw.vidasscrsvr

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.Uri
import android.service.dreams.DreamService
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

class VideoDreamService : DreamService() {

    private var player: ExoPlayer? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var scaleMode = "zoom"
    private var textureView: TextureView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false

        val prefs = getSharedPreferences("vid_scrsvr_prefs", MODE_PRIVATE)
        val uriString = prefs.getString("video_uri", null) ?: run { finish(); return }
        val soundOn = prefs.getBoolean("sound_on", false)
        val volume = prefs.getFloat("volume", 0.5f)
        scaleMode = prefs.getString("scale_mode", "zoom") ?: "zoom"

        val tv = TextureView(this)
        tv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        textureView = tv
        setContentView(tv)

        val uri = Uri.parse(uriString)

        player = ExoPlayer.Builder(this).build().also { p ->
            p.setMediaItem(MediaItem.fromUri(uri))
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.volume = if (soundOn) volume else 0f

            p.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    videoWidth = size.width
                    videoHeight = size.height
                    applyScale()
                }
            })

            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    p.setVideoSurface(Surface(st))
                    p.prepare()
                    p.play()
                }
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                    applyScale()
                }
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    p.setVideoSurface(null)
                    return true
                }
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
    }

    private fun applyScale() {
        val tv = textureView ?: return
        val vw = videoWidth.takeIf { it > 0 } ?: return
        val vh = videoHeight.takeIf { it > 0 } ?: return

        val sw = resources.displayMetrics.widthPixels.toFloat()
        val sh = resources.displayMetrics.heightPixels.toFloat()

        val matrix = Matrix()

        when (scaleMode) {
            "fit" -> {
                // scale uniformly so entire video fits, centered, with letterbox/pillarbox
                val scale = minOf(sw / vw, sh / vh)
                val scaledW = vw * scale
                val scaledH = vh * scale
                matrix.setScale(scaledW / sw, scaledH / sh)
                matrix.postTranslate((sw - scaledW) / 2f, (sh - scaledH) / 2f)
            }
            "stretch" -> {
                // just fill the whole surface, ratio be damned
                matrix.setScale(1f, 1f)
            }
            "zoom" -> {
                // scale uniformly so video covers entire screen, crop the overflow
                val scale = maxOf(sw / vw, sh / vh)
                val scaledW = vw * scale
                val scaledH = vh * scale
                matrix.setScale(scaledW / sw, scaledH / sh)
                matrix.postTranslate((sw - scaledW) / 2f, (sh - scaledH) / 2f)
            }
            "adapt" -> {
                // same as fit — intentional black bars, nothing cropped, nothing stretched
                val scale = minOf(sw / vw, sh / vh)
                val scaledW = vw * scale
                val scaledH = vh * scale
                matrix.setScale(scaledW / sw, scaledH / sh)
                matrix.postTranslate((sw - scaledW) / 2f, (sh - scaledH) / 2f)
            }
        }

        tv.post { tv.setTransform(matrix) }
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
        textureView = null
    }
}
