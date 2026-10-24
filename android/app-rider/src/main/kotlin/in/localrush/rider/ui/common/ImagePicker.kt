package `in`.localrush.rider.ui.common

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** Camera + gallery picker. Camera writes to a FileProvider cache file (see res/xml/file_paths.xml). */
class ImagePicker internal constructor(
    val fromCamera: () -> Unit,
    val fromGallery: () -> Unit,
)

@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): ImagePicker {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    val pendingUri = remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri.value
        if (ok && uri != null) callback.value(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = newCaptureUri(context)
            pendingUri.value = uri
            takePicture.launch(uri)
        }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) callback.value(uri)
    }
    return remember {
        ImagePicker(
            fromCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
            fromGallery = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

private fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
