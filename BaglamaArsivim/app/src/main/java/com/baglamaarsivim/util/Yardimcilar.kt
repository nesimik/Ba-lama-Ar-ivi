package com.baglamaarsivim.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val TR: Locale = Locale("tr", "TR")

fun tarihYaz(ms: Long): String = SimpleDateFormat("d MMM yyyy", TR).format(Date(ms))
fun tarihSaatYaz(ms: Long): String = SimpleDateFormat("d MMM yyyy HH:mm", TR).format(Date(ms))

fun boyutYaz(b: Long): String = when {
    b >= 1024L * 1024 * 1024 -> String.format(TR, "%.2f GB", b / (1024.0 * 1024 * 1024))
    b >= 1024L * 1024 -> String.format(TR, "%.1f MB", b / (1024.0 * 1024))
    b >= 1024 -> String.format(TR, "%.0f KB", b / 1024.0)
    else -> "$b B"
}

fun sureYaz(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

/** Akıştan akışa kopyalar; toplam biliniyorsa ilerlemeyi (0..1) bildirir. */
fun kopyala(girdi: InputStream, cikti: OutputStream, toplam: Long, ilerleme: (Float) -> Unit = {}): Long {
    val tampon = ByteArray(256 * 1024)
    var kopyalanan = 0L
    var sonBildirim = 0L
    while (true) {
        val n = girdi.read(tampon)
        if (n < 0) break
        cikti.write(tampon, 0, n)
        kopyalanan += n
        val simdi = System.currentTimeMillis()
        if (toplam > 0 && simdi - sonBildirim > 200) {
            sonBildirim = simdi
            ilerleme((kopyalanan.toFloat() / toplam).coerceIn(0f, 1f))
        }
    }
    ilerleme(1f)
    return kopyalanan
}

/** Okunan bayt sayısını tutan akış (zip geri yüklemede ilerleme için). */
class SayacliAkis(akis: InputStream) : FilterInputStream(akis) {
    var sayac = 0L
        private set

    override fun read(): Int = super.read().also { if (it >= 0) sayac++ }
    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) sayac += it }
}

data class UriBilgi(val ad: String, val boyut: Long, val mime: String?)

fun uriBilgisi(ctx: Context, uri: Uri, varsayilanMime: String? = null): UriBilgi {
    var ad: String? = null
    var boyut = -1L
    runCatching {
        ctx.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && !c.isNull(i)) ad = c.getString(i)
                val j = c.getColumnIndex(OpenableColumns.SIZE)
                if (j >= 0 && !c.isNull(j)) boyut = c.getLong(j)
            }
        }
    }
    val mime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull()
        ?.takeIf { it != "application/octet-stream" } ?: varsayilanMime
    return UriBilgi(ad ?: uri.lastPathSegment ?: "dosya", boyut, mime)
}

fun uzanti(ad: String): String = ad.substringAfterLast('.', "").lowercase(Locale.ROOT)
fun uzantisiz(ad: String): String = if (ad.contains('.')) ad.substringBeforeLast('.') else ad
fun mimedenUzanti(mime: String?): String? =
    mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }

fun uzantidanMime(ad: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(uzanti(ad)) ?: "application/octet-stream"

fun dosyaUri(ctx: Context, f: File): Uri =
    FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

/** Dosyayı cihazdaki uygun bir uygulamayla açar. Uygulama yoksa false döner. */
fun disaridaAc(ctx: Context, f: File, mime: String): Boolean {
    val niyet = Intent(Intent.ACTION_VIEW)
        .setDataAndType(dosyaUri(ctx, f), mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        ctx.startActivity(niyet); true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** Büyük resimleri bellek dostu şekilde, EXIF yönünü düzelterek yükler. */
fun resimYukle(f: File, maksKenar: Int): Bitmap? {
    val sinir = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, sinir)
    if (sinir.outWidth <= 0 || sinir.outHeight <= 0) return null
    var oran = 1
    while (sinir.outWidth / (oran * 2) >= maksKenar || sinir.outHeight / (oran * 2) >= maksKenar) oran *= 2
    val secenek = BitmapFactory.Options().apply { inSampleSize = oran; inMutable = true }
    val bmp = BitmapFactory.decodeFile(f.absolutePath, secenek) ?: return null
    val derece = runCatching {
        when (ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (derece == 0f) return bmp
    val m = Matrix().apply { postRotate(derece) }
    val donmus = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    if (donmus != bmp) bmp.recycle()
    return if (donmus.isMutable) donmus else donmus.copy(Bitmap.Config.ARGB_8888, true)
}
