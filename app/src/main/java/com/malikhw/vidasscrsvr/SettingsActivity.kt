package com.malikhw.vidasscrsvr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.malikhw.vidasscrsvr.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { getSharedPreferences("vid_scrsvr_prefs", MODE_PRIVATE) }

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult

        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        prefs.edit().putString("video_uri", uri.toString()).apply()
        binding.tvSelectedFile.text = getFileName(uri) ?: uri.lastPathSegment ?: "some video file"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSavedStuff()
        setupListeners()
    }

    private fun loadSavedStuff() {
        val uriString = prefs.getString("video_uri", null)
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            binding.tvSelectedFile.text = getFileName(uri) ?: "video selected"
        } else {
            binding.tvSelectedFile.text = getString(R.string.no_video_selected)
        }

        val soundOn = prefs.getBoolean("sound_on", false)
        binding.cbSound.isChecked = soundOn
        binding.layoutVolumeSlider.visibility = if (soundOn) View.VISIBLE else View.GONE
        binding.seekVolume.progress = (prefs.getFloat("volume", 0.5f) * 100).toInt()

        binding.cbLoop.isChecked = prefs.getBoolean("loop", true)

        if (prefs.getBoolean("scale_fill", true)) {
            binding.rbFill.isChecked = true
        } else {
            binding.rbFit.isChecked = true
        }
    }

    private fun setupListeners() {
        binding.btnChooseVideo.setOnClickListener {
            videoPicker.launch(arrayOf("video/mp4", "video/*"))
        }

        binding.cbSound.setOnCheckedChangeListener { _, checked ->
            binding.layoutVolumeSlider.visibility = if (checked) View.VISIBLE else View.GONE
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        }

        binding.btnPreview.setOnClickListener {
            saveSettings()
            if (prefs.getString("video_uri", null) == null) {
                Toast.makeText(this, "Pick a video first lol", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_MAIN)
            intent.setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Preview failed — use the system settings button instead", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnSystemSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
            } catch (e: Exception) {
                try {
                    val intent = Intent()
                    intent.setClassName("com.android.settings", "com.android.settings.Settings\$DreamSettingsActivity")
                    startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "Your phone really doesn't want you to find this setting huh", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnDonate.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://malikhw.github.io/donate")))
        }

        binding.btnSourceCode.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/MalikHw/vidasscreensaver")))
        }
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putBoolean("sound_on", binding.cbSound.isChecked)
            putFloat("volume", binding.seekVolume.progress / 100f)
            putBoolean("loop", binding.cbLoop.isChecked)
            putBoolean("scale_fill", binding.rbFill.isChecked)
            apply()
        }
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (e: Exception) {
            null
        }
    }
}
