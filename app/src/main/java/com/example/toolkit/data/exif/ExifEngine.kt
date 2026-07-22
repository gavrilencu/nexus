package com.example.toolkit.data.exif

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExifField(val group: String, val label: String, val value: String)

data class ExifResult(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String?,
    val hasGps: Boolean = false,
    val latLon: Pair<Double, Double>? = null,
    val fields: List<ExifField> = emptyList(),
    val privacyNotes: List<String> = emptyList(),
    val error: String? = null
)

/**
 * EXIF / image metadata extractor. Reads the full EXIF tag set from a picked
 * image (via androidx.ExifInterface), including GPS coordinates, capture
 * device, timestamps and software — then flags privacy-relevant leakage
 * (location, device serial, owner name).
 */
class ExifEngine {

    private val tagGroups: List<Triple<String, String, String>> = listOf(
        Triple("Image", "Dimensions", ExifInterface.TAG_IMAGE_WIDTH),
        Triple("Image", "Height", ExifInterface.TAG_IMAGE_LENGTH),
        Triple("Image", "Orientation", ExifInterface.TAG_ORIENTATION),
        Triple("Image", "Compression", ExifInterface.TAG_COMPRESSION),
        Triple("Image", "X Resolution", ExifInterface.TAG_X_RESOLUTION),
        Triple("Image", "Y Resolution", ExifInterface.TAG_Y_RESOLUTION),
        Triple("Camera", "Make", ExifInterface.TAG_MAKE),
        Triple("Camera", "Model", ExifInterface.TAG_MODEL),
        Triple("Camera", "Lens", ExifInterface.TAG_LENS_MODEL),
        Triple("Camera", "F-Number", ExifInterface.TAG_F_NUMBER),
        Triple("Camera", "Exposure Time", ExifInterface.TAG_EXPOSURE_TIME),
        Triple("Camera", "ISO", ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
        Triple("Camera", "Focal Length", ExifInterface.TAG_FOCAL_LENGTH),
        Triple("Camera", "Flash", ExifInterface.TAG_FLASH),
        Triple("Camera", "White Balance", ExifInterface.TAG_WHITE_BALANCE),
        Triple("Time", "Date/Time Original", ExifInterface.TAG_DATETIME_ORIGINAL),
        Triple("Time", "Date/Time Digitized", ExifInterface.TAG_DATETIME_DIGITIZED),
        Triple("Time", "Date/Time", ExifInterface.TAG_DATETIME),
        Triple("Time", "Offset Time", ExifInterface.TAG_OFFSET_TIME),
        Triple("Software", "Software", ExifInterface.TAG_SOFTWARE),
        Triple("Software", "Artist", ExifInterface.TAG_ARTIST),
        Triple("Software", "Copyright", ExifInterface.TAG_COPYRIGHT),
        Triple("Software", "Camera Owner", ExifInterface.TAG_CAMERA_OWNER_NAME),
        Triple("Software", "Body Serial", ExifInterface.TAG_BODY_SERIAL_NUMBER),
        Triple("GPS", "Latitude Ref", ExifInterface.TAG_GPS_LATITUDE_REF),
        Triple("GPS", "Latitude", ExifInterface.TAG_GPS_LATITUDE),
        Triple("GPS", "Longitude Ref", ExifInterface.TAG_GPS_LONGITUDE_REF),
        Triple("GPS", "Longitude", ExifInterface.TAG_GPS_LONGITUDE),
        Triple("GPS", "Altitude", ExifInterface.TAG_GPS_ALTITUDE),
        Triple("GPS", "Timestamp", ExifInterface.TAG_GPS_TIMESTAMP),
        Triple("GPS", "Date Stamp", ExifInterface.TAG_GPS_DATESTAMP),
        Triple("GPS", "Processing", ExifInterface.TAG_GPS_PROCESSING_METHOD)
    )

    suspend fun extract(context: Context, uri: Uri): ExifResult = withContext(Dispatchers.IO) {
        val (name, size) = queryMeta(context, uri)
        val mime = context.contentResolver.getType(uri)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return@withContext ExifResult(name, size, mime, error = "Cannot open file")
                val exif = ExifInterface(input)

                val fields = ArrayList<ExifField>()
                tagGroups.forEach { (group, label, tag) ->
                    val v = exif.getAttribute(tag)
                    if (!v.isNullOrBlank()) fields.add(ExifField(group, label, v))
                }

                val latLng = exif.latLong
                val hasGps = latLng != null
                val privacy = ArrayList<String>()
                if (hasGps) privacy.add("GPS location embedded — reveals where the photo was taken.")
                if (!exif.getAttribute(ExifInterface.TAG_BODY_SERIAL_NUMBER).isNullOrBlank())
                    privacy.add("Camera body serial number present — links image to a specific device.")
                if (!exif.getAttribute(ExifInterface.TAG_CAMERA_OWNER_NAME).isNullOrBlank())
                    privacy.add("Camera owner name present.")
                if (!exif.getAttribute(ExifInterface.TAG_ARTIST).isNullOrBlank())
                    privacy.add("Artist/author metadata present.")
                if (!exif.getAttribute(ExifInterface.TAG_SOFTWARE).isNullOrBlank())
                    privacy.add("Editing software identified — may reveal workflow.")

                ExifResult(
                    fileName = name, fileSize = size, mimeType = mime,
                    hasGps = hasGps,
                    latLon = latLng?.let { it[0] to it[1] },
                    fields = fields,
                    privacyNotes = if (privacy.isEmpty()) listOf("No obvious privacy-sensitive metadata found.") else privacy
                )
            }
        } catch (e: Exception) {
            ExifResult(name, size, mime, error = e.message ?: "Failed to read EXIF")
        }
    }

    private fun queryMeta(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "image"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0) size = c.getLong(si)
                }
            }
        }
        return name to size
    }
}
