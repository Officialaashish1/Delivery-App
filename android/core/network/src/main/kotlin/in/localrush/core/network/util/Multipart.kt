package `in`.localrush.core.network.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File

/** Helpers for multipart uploads (rider docs/photo, partner logo/cover, delivery proof, ticket attachments). */
object Multipart {
    private val TEXT = "text/plain".toMediaTypeOrNull()

    fun text(value: String): RequestBody = value.toRequestBody(TEXT)
    fun text(value: Number): RequestBody = value.toString().toRequestBody(TEXT)
    fun text(value: Boolean): RequestBody = value.toString().toRequestBody(TEXT)

    /** Build `{"a": body, ...}` map of text fields for `@PartMap`. */
    fun fields(vararg pairs: Pair<String, Any?>): Map<String, RequestBody> =
        pairs.filter { it.second != null }.associate { (k, v) -> k to v.toString().toRequestBody(TEXT) }

    fun filePart(fieldName: String, file: File, mimeType: String? = null): MultipartBody.Part {
        val type = (mimeType ?: guessMime(file.name) ?: "application/octet-stream").toMediaTypeOrNull()
        return MultipartBody.Part.createFormData(fieldName, file.name, file.asRequestBody(type))
    }

    /** Stream a content:// or file:// Uri without copying to disk. */
    fun uriPart(context: Context, fieldName: String, uri: Uri, fileName: String? = null): MultipartBody.Part {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: guessMime(uri.toString()) ?: "application/octet-stream"
        val name = fileName ?: displayName(resolver, uri) ?: "upload.${MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"}"
        return MultipartBody.Part.createFormData(fieldName, name, UriRequestBody(resolver, uri, mime))
    }

    fun bytesPart(fieldName: String, fileName: String, bytes: ByteArray, mimeType: String = "image/jpeg"): MultipartBody.Part =
        MultipartBody.Part.createFormData(fieldName, fileName, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))

    private fun guessMime(name: String): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(name).ifEmpty { name.substringAfterLast('.', "") }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private class UriRequestBody(private val resolver: ContentResolver, private val uri: Uri, private val mime: String) : RequestBody() {
        override fun contentType() = mime.toMediaTypeOrNull()
        override fun contentLength(): Long = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
        override fun writeTo(sink: BufferedSink) {
            resolver.openInputStream(uri)?.use { input -> sink.outputStream().use { input.copyTo(it) } }
                ?: throw java.io.FileNotFoundException(uri.toString())
        }
    }
}
