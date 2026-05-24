package com.example.gallerysecurevieweruser

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.gallerysecurevieweruser.ui.theme.GallerySecureViewerUserTheme
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DEFAULT_GALLERY_PACKAGE = "app.grapheneos.gallery"
private const val DEBUG_GALLERY_PACKAGE = "app.grapheneos.gallery.debug"
private const val STAGING_GALLERY_PACKAGE = "app.grapheneos.gallery.staging"
private const val PERF_GALLERY_PACKAGE = "app.grapheneos.gallery.perf"
private const val GOOGLE_PHOTOS_PACKAGE = "com.google.android.apps.photos"
private const val STANDALONE_ACTIVITY =
    "com.dot.gallery.feature_node.presentation.standalone.StandaloneActivity"

private const val ACTION_FAKE_SECURE =
    "com.example.gallerysecurevieweruser.action.secure_but_not_review"
private const val PREFS_NAME = "gallery_review_tester"
private const val KEY_TARGET_PACKAGE = "target_package"
private const val KEY_PRIMARY_URI = "primary_uri"
private const val KEY_SECONDARY_URI = "secondary_uri"
private const val KEY_USE_NEW_TASK = "use_new_task"
private val CAMERA_RELATIVE_PATH = "${Environment.DIRECTORY_DCIM}/Camera"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        setContent {
            GallerySecureViewerUserTheme {
                TesterScreen()
            }
        }
    }
}

@Composable
private fun TesterScreen() {
    val context = LocalContext.current
    val launchView = LocalView.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    val isDeviceLocked = context.isKeyguardLocked()
    var targetPackage by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_TARGET_PACKAGE, DEFAULT_GALLERY_PACKAGE) ?: DEFAULT_GALLERY_PACKAGE)
    }
    var primaryUri by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_PRIMARY_URI, "") ?: "")
    }
    var secondaryUri by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_SECONDARY_URI, "") ?: "")
    }
    var log by rememberSaveable {
        mutableStateOf("No test media has been created yet.")
    }
    var useNewTask by rememberSaveable {
        mutableStateOf(prefs.getBoolean(KEY_USE_NEW_TASK, true))
    }
    val hasPrimary = primaryUri.isNotBlank()
    val hasSecondary = secondaryUri.isNotBlank()
    val hasTargetPackage = targetPackage.isNotBlank()
    val isGooglePhotosTarget = targetPackage == GOOGLE_PHOTOS_PACKAGE
    val unlockedOnlyEnabled = hasPrimary && !isDeviceLocked

    fun updateTargetPackage(value: String) {
        targetPackage = value.trim()
        prefs.edit { putString(KEY_TARGET_PACKAGE, targetPackage) }
    }

    fun updateUseNewTask(value: Boolean) {
        useNewTask = value
        prefs.edit { putBoolean(KEY_USE_NEW_TASK, value) }
    }

    fun updateMediaPair(pair: MediaPair) {
        primaryUri = pair.primary.toString()
        secondaryUri = pair.secondary.toString()
        prefs.edit {
            putString(KEY_PRIMARY_URI, primaryUri)
            putString(KEY_SECONDARY_URI, secondaryUri)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Gallery review intent tester",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "This app creates two clearly labeled camera-style test images in MediaStore's DCIM/Camera location, then sends controlled review intents to the target app."
            )
            Text(
                text = if (isDeviceLocked) {
                    "Locked mode: run the secure review checks below."
                } else {
                    "Unlocked mode: create test media, then run viewer checks or lock the device for secure review."
                },
                fontWeight = FontWeight.SemiBold
            )

            Section(title = "Target") {
                OutlinedTextField(
                    value = targetPackage,
                    onValueChange = ::updateTargetPackage,
                    label = { Text("Target package") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton("Release", DEFAULT_GALLERY_PACKAGE, ::updateTargetPackage)
                    PresetButton("Debug", DEBUG_GALLERY_PACKAGE, ::updateTargetPackage)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton("Staging", STAGING_GALLERY_PACKAGE, ::updateTargetPackage)
                    PresetButton("Perf", PERF_GALLERY_PACKAGE, ::updateTargetPackage)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton("GooglePhotos", GOOGLE_PHOTOS_PACKAGE, ::updateTargetPackage)
                    PresetButton("Any", "", ::updateTargetPackage)
                }
                Text("Any leaves the package unset so Android resolves the handler.")
            }

            Section(title = "Launch options") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New task flags",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = useNewTask,
                        onCheckedChange = ::updateUseNewTask
                    )
                }
                Text(
                    text = if (useNewTask) {
                        "Launches from the current view with NEW_TASK and MULTIPLE_TASK."
                    } else {
                        "Launches from the current view without NEW_TASK or MULTIPLE_TASK."
                    }
                )
            }

            Section(title = "Test media") {
                Text(
                    text = if (isDeviceLocked) {
                        "If the secure review buttons are disabled, create a media pair now."
                    } else {
                        "Create a media pair before running tests. The saved URIs are reused after locking."
                    }
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            val pair = createTestMediaPair(context)
                            updateMediaPair(pair)
                            log = "Created camera primary:\n${pair.primary}\n\nCreated camera secondary:\n${pair.secondary}\n\nMediaStore path: $CAMERA_RELATIVE_PATH"
                        } catch (e: Exception) {
                            log = "Failed to create test media: ${e.message}"
                            Toast.makeText(context, log, Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Create camera media pair")
                }
                SelectionContainer {
                    Text(
                        text = "Primary: ${primaryUri.ifBlank { "not created" }}\nSecondary: ${secondaryUri.ifBlank { "not created" }}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (isDeviceLocked) {
                Section(title = "Secure review tests") {
                    Text("First use no secondary. If the target app reaches the secondary image anyway, it expanded beyond the secure review contract.")
                    LaunchButton(
                        label = "Camera ACTION_REVIEW_SECURE (no ClipData)",
                        enabled = hasPrimary
                    ) {
                        log = launchGallery(
                            launchView = launchView,
                            targetPackage = targetPackage,
                            action = MediaStore.ACTION_REVIEW_SECURE,
                            primaryUri = primaryUri.toUri(),
                            clipUris = emptyList(),
                            explicitComponent = false,
                            useNewTask = useNewTask
                        )
                    }
                    LaunchButton(
                        label = "Camera ACTION_REVIEW_SECURE (ClipData: secondary image)",
                        enabled = hasPrimary && hasSecondary
                    ) {
                        log = launchGallery(
                            launchView = launchView,
                            targetPackage = targetPackage,
                            action = MediaStore.ACTION_REVIEW_SECURE,
                            primaryUri = primaryUri.toUri(),
                            clipUris = listOf(secondaryUri.toUri()),
                            explicitComponent = false,
                            useNewTask = useNewTask
                        )
                    }
                    if (hasTargetPackage && !isGooglePhotosTarget) {
                        Text("Use the fake secure action to check whether Gallery treats any action containing secure as lock screen eligible.")
                        LaunchButton(
                            label = "Launch fake secure action (ClipData: primary image)",
                            enabled = hasPrimary
                        ) {
                            log = launchGallery(
                                launchView = launchView,
                                targetPackage = targetPackage,
                                action = ACTION_FAKE_SECURE,
                                primaryUri = primaryUri.toUri(),
                                clipUris = listOf(primaryUri.toUri()),
                                explicitComponent = true,
                                useNewTask = useNewTask
                            )
                        }
                    }
                }
            } else {
                Section(title = "Unlocked viewer tests") {
                    Text("Look for whether the target app opens only the primary image or also exposes the secondary image from the same Camera album.")
                    LaunchButton(
                        label = "Launch explicit ACTION_VIEW (ClipData: primary image)",
                        enabled = unlockedOnlyEnabled && hasTargetPackage && !isGooglePhotosTarget
                    ) {
                        log = launchGallery(
                            launchView = launchView,
                            targetPackage = targetPackage,
                            action = Intent.ACTION_VIEW,
                            primaryUri = primaryUri.toUri(),
                            clipUris = listOf(primaryUri.toUri()),
                            explicitComponent = true,
                            useNewTask = useNewTask
                        )
                    }
                    LaunchButton(
                        label = "Launch explicit ACTION_REVIEW (ClipData: primary image)",
                        enabled = unlockedOnlyEnabled && hasTargetPackage && !isGooglePhotosTarget
                    ) {
                        log = launchGallery(
                            launchView = launchView,
                            targetPackage = targetPackage,
                            action = MediaStore.ACTION_REVIEW,
                            primaryUri = primaryUri.toUri(),
                            clipUris = listOf(primaryUri.toUri()),
                            explicitComponent = true,
                            useNewTask = useNewTask
                        )
                    }
                    LaunchButton(
                        label = "Launch package ACTION_VIEW (ClipData: primary image)",
                        enabled = unlockedOnlyEnabled
                    ) {
                        log = launchGallery(
                            launchView = launchView,
                            targetPackage = targetPackage,
                            action = Intent.ACTION_VIEW,
                            primaryUri = primaryUri.toUri(),
                            clipUris = listOf(Uri.parse(primaryUri)),
                            explicitComponent = false,
                            useNewTask = useNewTask
                        )
                    }
                    LaunchButton(
                        label = "Launch package ACTION_REVIEW (ClipData: primary image)",
                        enabled = unlockedOnlyEnabled
                    ) {
                        log = launchGallery(
                            launchView = launchView,
                            targetPackage = targetPackage,
                            action = MediaStore.ACTION_REVIEW,
                            primaryUri = primaryUri.toUri(),
                            clipUris = listOf(primaryUri.toUri()),
                            explicitComponent = false,
                            useNewTask = useNewTask
                        )
                    }
                    LaunchButton(
                        label = "Camera ACTION_REVIEW (no ClipData)",
                        enabled = unlockedOnlyEnabled
                    ) {
                        log = launchGallery(
                            launchView = launchView,
                            targetPackage = targetPackage,
                            action = MediaStore.ACTION_REVIEW,
                            primaryUri = primaryUri.toUri(),
                            clipUris = emptyList(),
                            explicitComponent = false,
                            useNewTask = useNewTask
                        )
                    }
                }
                Section(title = "Unlocked secure review") {
                    Text("MediaStore expects secure review to \"display on top of the lock screen while secured\" and says \"no other media access should be allowed\" without ClipData.")
                    Text("Launch secure review while unlocked, then press power to lock the device. Check whether the viewer remains visible over the lock screen.")
                    LaunchButton(
                        label = "Launch package ACTION_REVIEW_SECURE (no ClipData)",
                        enabled = unlockedOnlyEnabled
                    ) {
                        log = launchGallery(
                            launchView = launchView,
                            targetPackage = targetPackage,
                            action = MediaStore.ACTION_REVIEW_SECURE,
                            primaryUri = primaryUri.toUri(),
                            clipUris = emptyList(),
                            explicitComponent = false,
                            useNewTask = useNewTask
                        )
                    }
                    LaunchButton(
                        label = "Launch package ACTION_REVIEW_SECURE (ClipData: secondary image)",
                        enabled = unlockedOnlyEnabled && hasSecondary
                    ) {
                        log = launchGallery(
                            launchView = launchView,
                            targetPackage = targetPackage,
                            action = MediaStore.ACTION_REVIEW_SECURE,
                            primaryUri = primaryUri.toUri(),
                            clipUris = listOf(secondaryUri.toUri()),
                            explicitComponent = false,
                            useNewTask = useNewTask
                        )
                    }
                    LaunchButton(
                        label = "Launch explicit ACTION_REVIEW_SECURE (no ClipData)",
                        enabled = unlockedOnlyEnabled && hasTargetPackage && !isGooglePhotosTarget
                    ) {
                        log = launchGallery(
                            launchView = launchView,
                            targetPackage = targetPackage,
                            action = MediaStore.ACTION_REVIEW_SECURE,
                            primaryUri = Uri.parse(primaryUri),
                            clipUris = emptyList(),
                            explicitComponent = true,
                            useNewTask = useNewTask
                        )
                    }
                    LaunchButton(
                        label = "Launch explicit ACTION_REVIEW_SECURE (ClipData: secondary image)",
                        enabled = unlockedOnlyEnabled && hasSecondary && hasTargetPackage && !isGooglePhotosTarget
                    ) {
                        log = launchGallery(
                            launchView = launchView,
                            targetPackage = targetPackage,
                            action = MediaStore.ACTION_REVIEW_SECURE,
                            primaryUri = Uri.parse(primaryUri),
                            clipUris = listOf(Uri.parse(secondaryUri)),
                            explicitComponent = true,
                            useNewTask = useNewTask
                        )
                    }
                }
                Section(title = "Lock screen run") {
                    Text("In OS Settings, go to Wallpaper & style > Shortcuts and set Camera as a lock screen shortcut.")
                    Text("After creating media, lock the device and open the Camera shortcut. Select Gallery Review Tester as the still camera app with always open if Android asks (you might need to unlock to set the default).")
                    Text("The tester will reopen over the lock screen with only the secure review buttons shown.")
                }
            }

            Section(title = "Status") {
                SelectionContainer {
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun RowScope.PresetButton(label: String, value: String, onClick: (String) -> Unit) {
    OutlinedButton(
        modifier = Modifier.weight(1f),
        onClick = { onClick(value) }
    ) {
        Text(label)
    }
}

@Composable
private fun LaunchButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick
    ) {
        Text(label)
    }
}

private data class MediaPair(
    val primary: Uri,
    val secondary: Uri
)

private fun createTestMediaPair(context: Context): MediaPair {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val primary = insertTestImage(
        context = context,
        displayName = "IMG_${stamp}_primary.jpg",
        title = "PRIMARY URI SENT",
        subtitle = "Gallery should open only this item",
        background = Color.rgb(22, 92, 125)
    )
    val secondary = insertTestImage(
        context = context,
        displayName = "IMG_${stamp}_secondary.jpg",
        title = "SECONDARY NOT SENT",
        subtitle = "Seeing this proves bucket expansion",
        background = Color.rgb(132, 52, 42)
    )
    return MediaPair(primary = primary, secondary = secondary)
}

private fun insertTestImage(
    context: Context,
    displayName: String,
    title: String,
    subtitle: String,
    background: Int
): Uri {
    val resolver = context.contentResolver
    val nowMillis = System.currentTimeMillis()
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, CAMERA_RELATIVE_PATH)
        put(MediaStore.Images.Media.DATE_TAKEN, nowMillis)
        put(MediaStore.Images.Media.DATE_MODIFIED, nowMillis / 1000)
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val insertedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("MediaStore insert returned null")

    try {
        resolver.openOutputStream(insertedUri)?.use { output ->
            createLabelBitmap(title, subtitle, background).compress(
                Bitmap.CompressFormat.JPEG,
                95,
                output
            )
        } ?: throw IOException("Unable to open output stream")

        ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }.also { resolver.update(insertedUri, it, null, null) }
        val id = ContentUris.parseId(insertedUri)
        return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    } catch (e: Exception) {
        resolver.delete(insertedUri, null, null)
        throw e
    }
}

private fun createLabelBitmap(title: String, subtitle: String, background: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(background)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 92f
        typeface = Typeface.DEFAULT_BOLD
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 46f
    }
    val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(235, 235, 235)
        textSize = 32f
    }

    canvas.drawText(title, 96f, 420f, titlePaint)
    canvas.drawText(subtitle, 96f, 500f, subtitlePaint)
    canvas.drawText("Album: $CAMERA_RELATIVE_PATH", 96f, 600f, smallPaint)
    canvas.drawText("Generated by Gallery Review Tester", 96f, 655f, smallPaint)
    return bitmap
}

private fun launchGallery(
    launchView: View,
    targetPackage: String,
    action: String,
    primaryUri: Uri,
    clipUris: List<Uri>,
    explicitComponent: Boolean,
    useNewTask: Boolean
): String {
    val context = launchView.context
    val cleanPackage = targetPackage.trim()
    if (explicitComponent && cleanPackage.isEmpty()) {
        return "Explicit component launch requires a target package."
    }

    val intent = Intent(action).apply {
        if (explicitComponent) {
            component = ComponentName(cleanPackage, STANDALONE_ACTIVITY)
        } else {
            if (cleanPackage.isNotEmpty()) {
                setPackage(cleanPackage)
            }
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        setDataAndType(primaryUri, "image/jpeg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (useNewTask) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        if (clipUris.isNotEmpty()) {
            clipData = ClipData.newUri(context.contentResolver, "review media", clipUris.first()).apply {
                clipUris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
        }
    }

    return try {
        if (cleanPackage.isNotEmpty()) {
            context.grantUriPermission(cleanPackage, primaryUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipUris.forEach { uri ->
                context.grantUriPermission(cleanPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        launchView.context.startActivity(intent)
        val target = when {
            explicitComponent -> "$cleanPackage/$STANDALONE_ACTIVITY"
            cleanPackage.isEmpty() -> "Android resolver"
            else -> "$cleanPackage via package scoped resolution"
        }
        val clipText = if (clipUris.isEmpty()) {
            "none"
        } else {
            clipUris.joinToString(separator = "\n") { it.toString() }
        }
        val taskFlags = if (useNewTask) {
            "GRANT_READ_URI_PERMISSION, NEW_TASK, MULTIPLE_TASK"
        } else {
            "GRANT_READ_URI_PERMISSION"
        }
        "Started $target\nAction: $action\nFlags: $taskFlags\nData: $primaryUri\nClipData:\n$clipText"
    } catch (e: ActivityNotFoundException) {
        if (cleanPackage.isEmpty()) {
            "No matching activity found for Android resolver."
        } else {
            "Target activity not found for package $cleanPackage. Check the target package preset."
        }
    } catch (e: SecurityException) {
        "SecurityException while starting Gallery: ${e.message}"
    } catch (e: Exception) {
        "Failed to start Gallery: ${e.javaClass.simpleName}: ${e.message}"
    }
}

private fun Context.isKeyguardLocked(): Boolean =
    getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
