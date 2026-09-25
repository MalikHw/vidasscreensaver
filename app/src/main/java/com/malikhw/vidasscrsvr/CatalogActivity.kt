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
import com.google.android.material.color.DynamicColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONArray
import org.json.JSONObject
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
    private val prefs by lazy { getSharedPreferences("vid_scrsvr_prefs", MODE_PRIVATE) }
    private fun getDownloadedMap(): Map<String, String> {
        val raw = prefs.getString("downloaded_catalog", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveDownloadEntry(videoUrl: String, filePath: String) {
        val map = getDownloadedMap().toMutableMap()
        map[videoUrl] = filePath
        prefs.edit().putString("downloaded_catalog", JSONObject(map as Map<*, *>).toString()).apply()
    }

    private fun getAppliedUri(): String? = prefs.getString("video_uri", null)
    private fun applyLocalFile(file: File) {
        val uri = Uri.fromFile(file)
        prefs.edit().putString("video_uri", uri.toString()).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        findViewById<MaterialButton>(R.id.btnSubmitVideo).setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/MalikHw/vidasscreensaver/issues/new?template=video-request.md")
                )
            )
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
                    videos.add(
                        CatalogVideo(
                            name = obj.optString("name", "Untitled"),
                            creator = obj.optString("creator", "Unknown"),
                            videoUrl = obj.optString("video_url", ""),
                            thumbUrl = obj.optString("thumb_url", "")
                        )
                    )
                }

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (videos.isEmpty()) {
                        tvError.visibility = View.VISIBLE
                        tvError.text = "Catalog is empty. Be the first to submit!"
                    } else {
                        showCatalog(recycler, videos)
                    }
                }
            } catch (e: Exception) {
                // Network failed — fall back to previously downloaded videos
                val downloadedMap = getDownloadedMap()
                if (downloadedMap.isNotEmpty()) {
                    val offlineVideos = downloadedMap.keys.mapNotNull { url ->
                        val path = downloadedMap[url] ?: return@mapNotNull null
                        if (!File(path).exists()) return@mapNotNull null
                        val videoFile = File(path)
                        val safeName = videoFile.nameWithoutExtension
                        val fileName = safeName
                            .removePrefix("catalog_")
                            .replace("_", " ")

                        val pngThumb = File(filesDir, "${safeName}_thumb.png")
                        val jpgThumb = File(filesDir, "${safeName}_thumb.jpg")
                        val thumbPath = when {
                            pngThumb.exists() -> pngThumb.absolutePath
                            jpgThumb.exists() -> jpgThumb.absolutePath
                            else -> ""
                        }

                        CatalogVideo(
                            name = fileName,
                            creator = "Downloaded",
                            videoUrl = url,
                            thumbUrl = thumbPath
                        )
                    }
                    mainHandler.post {
                        progressBar.visibility = View.GONE
                        if (offlineVideos.isEmpty()) {
                            tvError.visibility = View.VISIBLE
                            tvError.text = "Couldn't load catalog. Check your connection."
                        } else {
                            tvError.visibility = View.VISIBLE
                            tvError.text = "Offline — showing downloaded videos only"
                            showCatalog(recycler, offlineVideos)
                        }
                    }
                } else {
                    mainHandler.post {
                        progressBar.visibility = View.GONE
                        tvError.visibility = View.VISIBLE
                        tvError.text = "Couldn't load catalog. Check your connection."
                    }
                }
            }
        }
    }

    private fun showCatalog(recycler: RecyclerView, videos: List<CatalogVideo>) {
        recycler.adapter = CatalogAdapter(
            items = videos,
            downloadedMap = getDownloadedMap(),
            appliedUri = getAppliedUri()
        ) { video ->
            handleVideoClick(video)
        }
    }

    private fun handleVideoClick(video: CatalogVideo) {
        val downloadedMap = getDownloadedMap()
        val localPath = downloadedMap[video.videoUrl]
        val localFile = if (localPath != null) File(localPath) else null
        val appliedUri = getAppliedUri()
        when {
            localFile != null && localFile.exists() &&
                    Uri.fromFile(localFile).toString() == appliedUri -> {
                Toast.makeText(this, "${video.name} is already in use!", Toast.LENGTH_SHORT).show()
            }

            localFile != null && localFile.exists() -> {
                applyLocalFile(localFile)
                Toast.makeText(this, "${video.name} applied!", Toast.LENGTH_SHORT).show()
                val recycler = findViewById<RecyclerView>(R.id.rvCatalog)
                (recycler.adapter as? CatalogAdapter)?.updateState(
                    newDownloadedMap = getDownloadedMap(),
                    newAppliedUri = getAppliedUri()
                )
                setResult(RESULT_OK)
            }

            else -> startDownload(video)
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
                val total = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: conn.contentLength.toLong()
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

                if (video.thumbUrl.isNotEmpty() && video.thumbUrl.startsWith("http")) {
                    try {
                        val thumbExt = if (video.thumbUrl.contains(".png", ignoreCase = true)) ".png" else ".jpg"
                        val destThumbFile = File(filesDir, "catalog_${video.name.replace(" ", "_")}_thumb$thumbExt")
                        val tConn = URL(video.thumbUrl).openConnection() as HttpURLConnection
                        tConn.connectTimeout = 5000
                        tConn.readTimeout = 10000
                        val tInput = tConn.inputStream
                        val tOut = FileOutputStream(destThumbFile)
                        val tBuf = ByteArray(8192)
                        var tRead: Int
                        while (tInput.read(tBuf).also { tRead = it } != -1) {
                            tOut.write(tBuf, 0, tRead)
                        }
                        tOut.flush()
                        tOut.close()
                        tInput.close()
                        tConn.disconnect()
                    } catch (_: Exception) {
                    }
                }

                saveDownloadEntry(video.videoUrl, destFile.absolutePath)
                applyLocalFile(destFile)
                mainHandler.post {
                    dialog.dismiss()
                    Toast.makeText(this, "${video.name} applied!", Toast.LENGTH_SHORT).show()
                    val recycler = findViewById<RecyclerView>(R.id.rvCatalog)
                    (recycler.adapter as? CatalogAdapter)?.updateState(
                        newDownloadedMap = getDownloadedMap(),
                        newAppliedUri = getAppliedUri()
                    )
                    setResult(RESULT_OK)
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
    private var downloadedMap: Map<String, String>,
    private var appliedUri: String?,
    private val onDownload: (CatalogVideo) -> Unit
) : RecyclerView.Adapter<CatalogAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.ivThumb)
        val title: TextView = v.findViewById(R.id.tvTitle)
        val creator: TextView = v.findViewById(R.id.tvCreator)
        val checkmark: ImageView = v.findViewById(R.id.ivCheckmark)
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
        val localPath = downloadedMap[video.videoUrl]
        val localFile = if (localPath != null) java.io.File(localPath) else null
        val isApplied = localFile != null && localFile.exists() &&
                Uri.fromFile(localFile).toString() == appliedUri
        val isDownloaded = localFile != null && localFile.exists()
        holder.checkmark.visibility = if (isApplied) View.VISIBLE else View.GONE
        holder.itemView.alpha = when {
            isApplied -> 1f
            isDownloaded -> 0.85f
            else -> 1f
        }

        holder.thumb.setImageResource(android.R.drawable.ic_menu_slideshow)

        if (video.thumbUrl.isNotEmpty()) {
            if (!video.thumbUrl.startsWith("http")) {
                val bmp = android.graphics.BitmapFactory.decodeFile(video.thumbUrl)
                if (bmp != null) {
                    holder.thumb.setImageBitmap(bmp)
                }
            } else {
                val safeName = video.name.replace(" ", "_")
                val pngThumb = File(holder.itemView.context.filesDir, "catalog_${safeName}_thumb.png")
                val jpgThumb = File(holder.itemView.context.filesDir, "catalog_${safeName}_thumb.jpg")
                val localThumb = when {
                    pngThumb.exists() -> pngThumb
                    jpgThumb.exists() -> jpgThumb
                    else -> null
                }
                if (localThumb != null) {
                    val bmp = android.graphics.BitmapFactory.decodeFile(localThumb.absolutePath)
                    if (bmp != null) {
                        holder.thumb.setImageBitmap(bmp)
                    }
                } else {
                    Thread {
                        try {
                            val conn = URL(video.thumbUrl).openConnection() as HttpURLConnection
                            conn.connectTimeout = 5000
                            val bmp = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                            conn.disconnect()
                            holder.thumb.post { holder.thumb.setImageBitmap(bmp) }
                        } catch (_: Exception) {
                        }
                    }.start()
                }
            }
        }

        val anim = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.slide_up_fade_in)
        anim.startOffset = (position * 50L).coerceAtMost(300L)
        holder.itemView.startAnimation(anim)

        holder.itemView.setOnClickListener { onDownload(video) }
    }

    override fun getItemCount() = items.size

    fun updateState(newDownloadedMap: Map<String, String>, newAppliedUri: String?) {
        downloadedMap = newDownloadedMap
        appliedUri = newAppliedUri
        notifyDataSetChanged()
    }
}
