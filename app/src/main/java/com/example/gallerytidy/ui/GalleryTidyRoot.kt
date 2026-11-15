package com.example.gallerytidy.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gallerytidy.util.AccessLevel
import com.example.gallerytidy.util.Permissions

@Composable
fun GalleryTidyRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val state by vm.ui.collectAsStateWithLifecycle()

    var access by remember { mutableStateOf(Permissions.level(context)) }
    var open by rememberSaveable { mutableStateOf<Category?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { access = Permissions.level(context) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> vm.onDeleteResult(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(Unit) {
        vm.deleteIntents.collect { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    // The user may grant access from system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = Permissions.level(context) }
    LaunchedEffect(access) { if (access != AccessLevel.NONE) vm.refresh() }

    BackHandler(enabled = open != null) { open = null }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            access == AccessLevel.NONE -> PermissionScreen(
                onGrant = { permissionLauncher.launch(Permissions.required()) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                }
            )
            open == null -> DashboardScreen(
                state = state,
                partialAccess = access == AccessLevel.PARTIAL,
                onGrantFull = { permissionLauncher.launch(Permissions.required()) },
                onOpen = { open = it },
                onRescan = { vm.refresh() },
                onThresholds = vm::updateThresholds
            )
            else -> {
                val category = open!!
                val items = when (category) {
                    Category.BLURRY -> state.blurry.map { ReviewItem(it, "Sharpness ${it.sharpness?.toInt()}") }
                    Category.DARK -> state.dark.map { ReviewItem(it, "Brightness ${it.brightness?.toInt()}") }
                    Category.BRIGHT -> state.bright.map { ReviewItem(it, "Brightness ${it.brightness?.toInt()}") }
                    Category.DUPLICATES -> state.duplicateExtras.map { ReviewItem(it, "Similar to a better shot") }
                }
                ReviewScreen(
                    title = when (category) {
                        Category.BLURRY -> "Blurry"
                        Category.DARK -> "Too dark"
                        Category.BRIGHT -> "Overexposed"
                        Category.DUPLICATES -> "Similar shots"
                    },
                    category = category,
                    items = items,
                    preselectAll = category == Category.DUPLICATES,
                    onBack = { open = null },
                    onKeep = vm::keep,
                    onDelete = vm::requestDelete
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("Gallery Tidy", style = MaterialTheme.typography.headlineMedium)
        Text(
            "To suggest blurry, badly exposed and near-duplicate photos, the app needs to read " +
                "your photos. Analysis happens entirely on this phone, and nothing is deleted " +
                "without your confirmation.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onGrant) { Text("Allow photo access") }
        OutlinedButton(onClick = onOpenSettings) { Text("Open app settings") }
    }
}
