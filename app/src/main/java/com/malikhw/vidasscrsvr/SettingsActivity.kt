package com.malikhw.vidasscrsvr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.malikhw.vidasscrsvr.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { getSharedPreferences("vid_scrsvr_prefs", MODE_PRIVATE) }
    private val orientationOptions = arrayOf("Portrait", "Landscape", "Landscape reversed", "Portrait reversed")

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putString("video_uri", uri.toString()).apply()
        val name = getVideoDisplayName(uri)
        binding.tvSelectedFile.text = "selected $name"
    }

    private val catalogLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uriString = prefs.getString("video_uri", null) ?: return@registerForActivityResult
                val uri = Uri.parse(uriString)
                val name = getVideoDisplayName(uri)
                binding.tvSelectedFile.text = "selected $name"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, orientationOptions)
        binding.spinnerOrientation.setAdapter(adapter)
        binding.spinnerOrientation.setOnItemClickListener { _, _, position, _ ->
            val selectedOption = orientationOptions.getOrNull(position) ?: "Portrait"
            val orientationVal = when (selectedOption) {
                "Portrait" -> "portrait"
                "Landscape" -> "landscape"
                "Landscape reversed" -> "landscape_reversed"
                "Portrait reversed" -> "portrait_reversed"
                else -> "landscape"
            }
            prefs.edit().putString("orientation", orientationVal).apply()
        }

        loadSavedStuff()
        setupListeners()
        runEntryAnimations()

        // TV remote (D-pad) support: give the first control initial focus so a
        // remote can start navigating immediately instead of landing nowhere.
        if (packageManager.hasSystemFeature("android.software.leanback") ||
            packageManager.hasSystemFeature("android.hardware.type.television")
        ) {
            binding.btnChooseVideo.requestFocus()
        }
    }

    private fun runEntryAnimations() {
        val groups = listOf(
            binding.groupHeader,
            binding.groupVideoCard,
            binding.groupPlaybackCard,
            binding.groupScaleCard,
            binding.groupOrientationCard,
            binding.groupButtons
        )
        groups.forEachIndexed { i, view ->
            view.visibility = View.VISIBLE
            val a = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in)
            a.startOffset = (i * 80).toLong()
            a.fillAfter = true
            view.alpha = 1f
            view.startAnimation(a)
        }
    }

    private fun loadSavedStuff() {
        val uriString = prefs.getString("video_uri", null)
        binding.tvSelectedFile.text = if (uriString != null) {
            val name = getVideoDisplayName(Uri.parse(uriString))
            "selected $name"
        } else {
            getString(R.string.no_video_selected)
        }

        val soundOn = prefs.getBoolean("sound_on", false)
        binding.cbSound.isChecked = soundOn
        binding.layoutVolumeSlider.visibility = if (soundOn) View.VISIBLE else View.GONE
        binding.seekVolume.progress = (prefs.getFloat("volume", 0.5f) * 100).toInt()
        binding.cbLoop.isChecked = prefs.getBoolean("loop", true)

        when (prefs.getString("scale_mode", "zoom")) {
            "fit" -> binding.rbFit.isChecked = true
            "stretch" -> binding.rbStretch.isChecked = true
            "zoom" -> binding.rbZoom.isChecked = true
            "adapt" -> binding.rbAdapt.isChecked = true
            else -> binding.rbZoom.isChecked = true
        }

        val orientationVal = prefs.getString("orientation", "landscape") ?: "landscape"
        val orientationText = when (orientationVal) {
            "portrait" -> "Portrait"
            "landscape" -> "Landscape"
            "landscape_reversed" -> "Landscape reversed"
            "portrait_reversed" -> "Portrait reversed"
            else -> "Landscape"
        }
        binding.spinnerOrientation.setText(orientationText, false)
    }

    private fun setupListeners() {
        binding.btnChooseVideo.setOnClickListener {
            videoPicker.launch(arrayOf("video/mp4", "video/*"))
        }

        binding.btnBrowseCatalog.setOnClickListener {
            catalogLauncher.launch(Intent(this, CatalogActivity::class.java))
        }

        binding.cbSound.setOnCheckedChangeListener { _, checked ->
            binding.layoutVolumeSlider.visibility = if (checked) View.VISIBLE else View.GONE
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSystemSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
            } catch (e: Exception) {
                try {
                    val i = Intent()
                    i.setClassName("com.android.settings", "com.android.settings.Settings\$DreamSettingsActivity")
                    startActivity(i)
                } catch (e2: Exception) {
                    Toast.makeText(
                        this,
                        "Your phone really doesn't want you to find this setting huh",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        binding.btnDonate.setOnClickListener {
            DonateDialog.show(this)
        }

        binding.btnInfo.setOnClickListener {
            AboutDialog.show(this)
        }

        binding.btnSourceCode.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/MalikHw/vidasscreensaver")))
        }
    }

    private fun saveSettings() {
        val scaleMode = when (binding.rgScale.checkedRadioButtonId) {
            R.id.rbFit -> "fit"
            R.id.rbStretch -> "stretch"
            R.id.rbZoom -> "zoom"
            R.id.rbAdapt -> "adapt"
            else -> "zoom"
        }
        val orientationText = binding.spinnerOrientation.text.toString()
        val orientationVal = when (orientationText) {
            "Portrait" -> "portrait"
            "Landscape" -> "landscape"
            "Landscape reversed" -> "landscape_reversed"
            "Portrait reversed" -> "portrait_reversed"
            else -> "landscape"
        }
        prefs.edit().apply {
            putBoolean("sound_on", binding.cbSound.isChecked)
            putFloat("volume", binding.seekVolume.progress / 100f)
            putBoolean("loop", binding.cbLoop.isChecked)
            putString("scale_mode", scaleMode)
            putString("orientation", orientationVal)
            apply()
        }
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(idx)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getVideoDisplayName(uri: Uri): String {
        val contentName = getFileName(uri)
        if (contentName != null) return contentName
        val lastSegment = uri.lastPathSegment ?: return "video file"
        val rawName = lastSegment.substringAfterLast("/")
        return if (rawName.startsWith("catalog_")) {
            rawName.removePrefix("catalog_").removeSuffix(".mp4").replace("_", " ")
        } else {
            rawName
        }
    }
}
