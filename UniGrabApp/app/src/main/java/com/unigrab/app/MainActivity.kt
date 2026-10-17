package com.unigrab.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.unigrab.app.ui.navigation.UniGrabNavHost
import com.unigrab.app.ui.screens.activity.ActivityViewModel
import com.unigrab.app.ui.screens.download.DownloadViewModel
import com.unigrab.app.ui.screens.library.LibraryViewModel
import com.unigrab.app.ui.screens.settings.SettingsViewModel
import com.unigrab.app.ui.theme.UniGrabTheme

class MainActivity : ComponentActivity() {

    private val downloadViewModel: DownloadViewModel by viewModels {
        DownloadViewModel.Factory((application as UniGrabApplication).downloadManager)
    }

    private val libraryViewModel: LibraryViewModel by viewModels {
        LibraryViewModel.Factory((application as UniGrabApplication).repository)
    }

    private val activityViewModel: ActivityViewModel by viewModels {
        ActivityViewModel.Factory(
            (application as UniGrabApplication).repository,
            (application as UniGrabApplication).downloadManager
        )
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory()
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Handle shared links
        handleSendIntent(intent)

        setContent {
            UniGrabTheme {
                UniGrabNavHost(
                    downloadViewModel = downloadViewModel,
                    libraryViewModel = libraryViewModel,
                    activityViewModel = activityViewModel,
                    settingsViewModel = settingsViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val url = extractUrl(sharedText)
                if (url != null) {
                    downloadViewModel.onUrlChanged(url)
                    downloadViewModel.searchUrl(this)
                }
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val words = text.split("\\s+".toRegex())
        return words.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
    }
}
