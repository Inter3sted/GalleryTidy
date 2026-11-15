package com.example.gallerytidy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.gallerytidy.ui.GalleryTidyRoot
import com.example.gallerytidy.ui.GalleryTidyTheme
import com.example.gallerytidy.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GalleryTidyTheme { GalleryTidyRoot(viewModel) }
        }
    }
}
