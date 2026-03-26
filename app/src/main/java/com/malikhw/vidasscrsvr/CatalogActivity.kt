package com.malikhw.vidasscrsvr

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class CatalogVideo(
    val name: String,
    val creator: String,
    val videoUrl: String,
    val thumbUrl: String
)

class CatalogActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler by lazy { android.os.Handler(mainLooper) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        findViewById<MaterialButton>(R.id.btnSubmitVideo).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/MalikHw/vidasscreensaver/issues/new?template=video-request.md")))
        }

        val recycler = findViewById<RecyclerView>(R.id.rvCatalog)
        val progressBar = findViewById<ProgressBar>(R.id.pbLoading)
        val tvError = findViewById<TextView>(R.id.tvError)

        recycler.layoutManager = GridLayoutManager(this, 2)

        executor.execute {
            try {
                val conn = URL("https://github.com/MalikHw/vidasscreensaver/raw/refs/heads/main/catalog.json")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val arr = JSONArray(json)
                val videos = mutableListOf<CatalogVideo>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    videos.add(CatalogVideo(
                        name = obj.optString("name", "Untitled"),
                        creator = obj.optString("creator", "Unknown"),
                        videoUrl = obj.optString("video_url", ""),
                        thumbUrl = obj.optString("thumb_url", "")
                    ))
                }

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (videos.isEmpty()) {
                        tvError.visibility = View.VISIBLE
                        tvError.text = "Catalog is empty. Be the first to submit!"
                    } else {
                        recycler.adapter = CatalogAdapter(videos) { video ->
                            startDownload(video)
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    tvError.visibility = View.VISIBLE
                    tvError.text = "Couldn't load catalog. Check your connection."
                }
            }
        }
    }

    private fun startDownload(video: CatalogVideo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_download, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDownloadTitle)
        val progress = dialogView.findViewById<LinearProgressIndicator>(R.id.downloadProgress)
        val tvPercent = dialogView.findViewById<TextView>(R.id.tvPercent)

        tvTitle.text = "Downloading ${video.name}…"
        progress.isIndeterminate = false

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()

        val destFile = File(filesDir, "catalog_${video.name.replace(" ", "_")}.mp4")

        executor.execute {
            try {
                val conn = URL(video.videoUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                val total = conn.contentLengthLong
                val input = conn.inputStream
                val out = FileOutputStream(destFile)
                val buf = ByteArray(8192)
                var downloaded = 0L
                var read: Int

                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    if (total > 0) {
                        val pct = (downloaded * 100 / total).toInt()
                        mainHandler.post {
                            progress.progress = pct
                            tvPercent.text = "$pct%"
                        }
                    }
                }
                out.flush()
                out.close()
                input.close()
                conn.disconnect()

                val uri = Uri.fromFile(destFile)
                val prefs = getSharedPreferences("vid_scrsvr_prefs", MODE_PRIVATE)
                prefs.edit().putString("video_uri", uri.toString()).apply()

                mainHandler.post {
                    dialog.dismiss()
                    Toast.makeText(this, "${video.name} applied!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                destFile.delete()
                mainHandler.post {
                    dialog.dismiss()
                    Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}

class CatalogAdapter(
    private val items: List<CatalogVideo>,
    private val onDownload: (CatalogVideo) -> Unit
) : RecyclerView.Adapter<CatalogAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.ivThumb)
        val title: TextView = v.findViewById(R.id.tvTitle)
        val creator: TextView = v.findViewById(R.id.tvCreator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_catalog_video, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val video = items[position]
        holder.title.text = video.name
        holder.creator.text = video.creator

        holder.thumb.setImageResource(android.R.drawable.ic_menu_slideshow)

        if (video.thumbUrl.isNotEmpty()) {
            Thread {
                try {
                    val conn = URL(video.thumbUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    val bmp = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                    conn.disconnect()
                    holder.thumb.post { holder.thumb.setImageBitmap(bmp) }
                } catch (_: Exception) {}
            }.start()
        }

        val anim = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.slide_up_fade_in)
        anim.startOffset = (position * 50L).coerceAtMost(300L)
        holder.itemView.startAnimation(anim)

        holder.itemView.setOnClickListener { onDownload(video) }
    }

    override fun getItemCount() = items.size
}
