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
    private var videoSurface: Surface? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false

        val prefs = getSharedPreferences("vid_scrsvr_prefs", MODE_PRIVATE)
        val uriString = prefs.getString("video_uri", null) ?: run { finish(); return }
        val soundOn = prefs.getBoolean("sound_on", false)
        val volume = prefs.getFloat("volume", 0.5f)
        val loop = prefs.getBoolean("loop", true)
        scaleMode = prefs.getString("scale_mode", "zoom") ?: "zoom"

        val tv = TextureView(this)
        tv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        textureView = tv
        setContentView(tv)

        tv.post { applyScale() }

        val uri = Uri.parse(uriString)

        player = ExoPlayer.Builder(this).build().also { p ->
            p.setMediaItem(MediaItem.fromUri(uri))
            p.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            p.volume = if (soundOn) volume else 0f

            p.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    videoWidth = size.width
                    videoHeight = size.height
                    applyScale()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    finish()
                }
            })

            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    videoSurface?.release()
                    videoSurface = Surface(st)
                    p.setVideoSurface(videoSurface)
                    p.prepare()
                    p.play()
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                    applyScale()
                }

                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    p.setVideoSurface(null)
                    videoSurface?.release()
                    videoSurface = null
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

        val rawW = tv.width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        val rawH = tv.height.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels.toFloat()

        val matrix = Matrix()

        when (scaleMode) {
            "fit" -> {
                // scale uniformly so entire video fits, centered, with letterbox/pillarbox
                val scale = minOf(rawW / vw, rawH / vh)
                val scaledW = vw * scale
                val scaledH = vh * scale
                matrix.setScale(scaledW / rawW, scaledH / rawH)
                matrix.postTranslate((rawW - scaledW) / 2f, (rawH - scaledH) / 2f)
            }

            "stretch" -> {
                matrix.setScale(1f, 1f)
            }

            "zoom" -> {
                val scale = maxOf(rawW / vw, rawH / vh)
                val scaledW = vw * scale
                val scaledH = vh * scale
                matrix.setScale(scaledW / rawW, scaledH / rawH)
                matrix.postTranslate((rawW - scaledW) / 2f, (rawH - scaledH) / 2f)
            }

            "adapt" -> {
                // same as fit — intentional black bars, nothing cropped, nothing stretched
                val scale = minOf(rawW / vw, rawH / vh)
                val scaledW = vw * scale
                val scaledH = vh * scale
                matrix.setScale(scaledW / rawW, scaledH / rawH)
                matrix.postTranslate((rawW - scaledW) / 2f, (rawH - scaledH) / 2f)
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
        player?.setVideoSurface(null)
        videoSurface?.release()
        videoSurface = null
        player?.release()
        player = null
        textureView = null
    }
}
