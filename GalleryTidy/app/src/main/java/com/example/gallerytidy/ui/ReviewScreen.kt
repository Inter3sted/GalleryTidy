package com.example.gallerytidy.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.gallerytidy.data.PhotoEntity
import com.example.gallerytidy.util.formatBytes

data class ReviewItem(val photo: PhotoEntity, val caption: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    title: String,
    category: Category,
    items: List<ReviewItem>,
    preselectAll: Boolean,
    onBack: () -> Unit,
    onKeep: (List<Long>) -> Unit,
    onDelete: (List<PhotoEntity>) -> Unit
) {
    // Selection survives rotation; stale ids (already deleted/kept) are filtered out below.
    var selected by rememberSaveable(category) {
        mutableStateOf(if (preselectAll) items.map { it.photo.id }.toSet() else emptySet())
    }
    val liveIds = items.map { it.photo.id }.toSet()
    val picked = selected intersect liveIds
    val pickedPhotos = items.filter { it.photo.id in picked }.map { it.photo }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$title (${items.size})") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = { selected = if (picked.size == items.size) emptySet() else liveIds }) {
                        Text(if (picked.size == items.size && items.isNotEmpty()) "Clear" else "Select all")
                    }
                }
            )
        },
        bottomBar = {
            if (picked.isNotEmpty()) {
                BottomAppBar {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onKeep(picked.toList()); selected = emptySet() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Keep") }
                        Button(
                            onClick = { onDelete(pickedPhotos); selected = emptySet() },
                            modifier = Modifier.weight(2f)
                        ) { Text("Delete ${picked.size} (${formatBytes(pickedPhotos.sumOf { it.sizeBytes })})") }
                    }
                }
            }
        }
    ) { inner ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text("Nothing to review here.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items, key = { it.photo.id }) { item ->
                    PhotoCell(item, item.photo.id in picked) {
                        selected = if (item.photo.id in picked) picked - item.photo.id else picked + item.photo.id
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(item: ReviewItem, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context).data(Uri.parse(item.photo.uri)).size(320).build(),
                contentDescription = item.photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium) }
            }
        }
        Text(item.caption, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(4.dp))
    }
}
