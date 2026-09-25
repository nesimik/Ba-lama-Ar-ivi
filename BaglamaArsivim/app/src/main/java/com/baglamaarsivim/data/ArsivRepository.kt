package com.baglamaarsivim.data

import android.content.Context
import android.net.Uri
import com.baglamaarsivim.util.kopyala
import com.baglamaarsivim.util.mimedenUzanti
import com.baglamaarsivim.util.tarihYaz
import com.baglamaarsivim.util.uriBilgisi
import com.baglamaarsivim.util.uzanti
import com.baglamaarsivim.util.uzantisiz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Tüm dosya ve veritabanı işlemlerinin tek adresi.
 * Dosyalar uygulamanın dahili depolamasında tutulur (Scoped Storage uyumlu, izin gerektirmez).
 */
class ArsivRepository(private val ctx: Context, val db: AppDatabase) {
    val dao: ArsivDao = db.dao()

    val videoDir = File(ctx.filesDir, "videolar").apply { mkdirs() }
    val dokDir = File(ctx.filesDir, "dokumanlar").apply { mkdirs() }
    /** Paylaşımdan gelen ve henüz türküye atanmamış dosyalar için geçici klasör */
    val gelenDir = File(ctx.cacheDir, "gelen").apply { mkdirs() }

    fun videoDosya(v: Video) = File(videoDir, v.dosyaAdi)
    fun videoDosya(dosyaAdi: String) = File(videoDir, dosyaAdi)
    fun dokDosya(d: Dokuman) = File(dokDir, d.dosya)

    fun gelenKlasorunuTemizle() {
        gelenDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun yeniTurku(ad: String): Long =
        dao.turkuEkle(Turku(ad = ad.trim(), ozelSiraNo = dao.maxSira() + 1))

    private fun benzersizAd(ext: String) =
        UUID.randomUUID().toString() + if (ext.isNotEmpty()) ".$ext" else ""

    private fun tasi(kaynak: File, hedef: File) {
        if (!kaynak.renameTo(hedef)) {
            kaynak.inputStream().use { i -> hedef.outputStream().use { o -> i.copyTo(o) } }
            kaynak.delete()
        }
    }

    /** Geçici klasördeki bir dosyayı video olarak arşive taşır. */
    suspend fun videoKaydet(turkuId: Long, kaynak: File, gorunenAd: String, tip: KaynakTipi): Long =
        withContext(Dispatchers.IO) {
            val hedef = File(videoDir, benzersizAd(uzanti(gorunenAd).ifEmpty { "mp4" }))
            tasi(kaynak, hedef)
            dao.videoEkle(
                Video(
                    turkuId = turkuId, baslik = videoBasligi(gorunenAd), dosyaAdi = hedef.name,
                    kaynakTipi = tip, boyutByte = hedef.length()
                )
            )
        }

    /** Geçici klasördeki bir dosyayı doküman olarak arşive taşır. */
    suspend fun dokumanKaydet(turkuId: Long?, kaynak: File, gorunenAd: String, tip: DokumanTipi): Long =
        withContext(Dispatchers.IO) {
            val hedef = File(dokDir, benzersizAd(uzanti(gorunenAd).ifEmpty { uzanti(kaynak.name) }))
            tasi(kaynak, hedef)
            dao.dokumanEkle(
                Dokuman(
                    turkuId = turkuId, dosyaAdi = gorunenAd, dosya = hedef.name, tip = tip,
                    boyutByte = hedef.length()
                )
            )
        }

    /** Dosya seçiciden gelen videoyu kopyalayarak ekler. */
    suspend fun uridenVideo(turkuId: Long, uri: Uri, ilerleme: (Float) -> Unit): Long =
        withContext(Dispatchers.IO) {
            val b = uriBilgisi(ctx, uri)
            val ext = uzanti(b.ad).ifEmpty { mimedenUzanti(b.mime) ?: "mp4" }
            val hedef = File(videoDir, benzersizAd(ext))
            val akis = ctx.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı: ${b.ad}")
            try {
                akis.use { i -> hedef.outputStream().use { o -> kopyala(i, o, b.boyut, ilerleme) } }
            } catch (e: Exception) {
                hedef.delete(); throw e
            }
            dao.videoEkle(
                Video(
                    turkuId = turkuId, baslik = videoBasligi(b.ad), dosyaAdi = hedef.name,
                    kaynakTipi = KaynakTipi.DOSYA, boyutByte = hedef.length()
                )
            )
        }

    /** Dosya seçiciden gelen dokümanı kopyalar. Desteklenmeyen türde false döner. */
    suspend fun uridenDokuman(turkuId: Long?, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val b = uriBilgisi(ctx, uri)
        val tip = dokumanTipi(b.mime, b.ad) ?: return@withContext false
        val ext = uzanti(b.ad).ifEmpty { mimedenUzanti(b.mime) ?: "" }
        val ad = if (uzanti(b.ad).isEmpty() && ext.isNotEmpty()) "${b.ad}.$ext" else b.ad
        val hedef = File(dokDir, benzersizAd(ext))
        val akis = ctx.contentResolver.openInputStream(uri) ?: return@withContext false
        akis.use { i -> hedef.outputStream().use { o -> i.copyTo(o) } }
        dao.dokumanEkle(Dokuman(turkuId = turkuId, dosyaAdi = ad, dosya = hedef.name, tip = tip, boyutByte = hedef.length()))
        true
    }

    suspend fun turkuSil(t: Turku, dokumanlarDa: Boolean) = withContext(Dispatchers.IO) {
        dao.videolar(t.id).forEach { videoDosya(it).delete() }
        dao.turkuVideolariniSil(t.id)
        if (dokumanlarDa) {
            dao.turkuDokumanlari(t.id).forEach { dokumanSil(it) }
        } else {
            dao.dokumanlariAyir(t.id)
        }
        dao.turkuSil(t)
    }

    suspend fun videoSil(v: Video) = withContext(Dispatchers.IO) {
        videoDosya(v).delete()
        dao.videoSil(v)
    }

    suspend fun dokumanSil(d: Dokuman) = withContext(Dispatchers.IO) {
        dokDosya(d).delete()
        dao.isaretleriSil(d.id)
        dao.dokumanSil(d)
    }

    /** Geri yüklemede "Tümünü değiştir" için: tüm kayıtları ve dosyaları siler. */
    suspend fun herSeyiSil() = withContext(Dispatchers.IO) {
        dao.isaretleriTemizle()
        dao.dokumanlariTemizle()
        dao.videolariTemizle()
        dao.turkuleriTemizle()
        videoDir.listFiles()?.forEach { it.delete() }
        dokDir.listFiles()?.forEach { it.delete() }
    }

    fun videoKullanimi(): Long = videoDir.listFiles()?.sumOf { it.length() } ?: 0L
    fun dokumanKullanimi(): Long = dokDir.listFiles()?.sumOf { it.length() } ?: 0L

    companion object {
        private val VIDEO_UZANTI = setOf("mp4", "3gp", "mkv", "webm", "mov", "m4v")
        private val RESIM_UZANTI = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")

        fun videoMu(mime: String?, ad: String): Boolean =
            mime?.startsWith("video/") == true || uzanti(ad) in VIDEO_UZANTI

        fun dokumanTipi(mime: String?, ad: String): DokumanTipi? {
            val ext = uzanti(ad)
            return when {
                mime == "application/pdf" || ext == "pdf" -> DokumanTipi.PDF
                mime?.startsWith("image/") == true || ext in RESIM_UZANTI -> DokumanTipi.IMAGE
                mime == "application/msword" ||
                    mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                    ext == "doc" || ext == "docx" -> DokumanTipi.WORD
                else -> null
            }
        }

        /** WhatsApp'ın "VID-20260925-WA0003" gibi adlarını okunur bir başlığa çevirir. */
        fun videoBasligi(ad: String): String {
            val a = uzantisiz(ad).trim()
            return if (a.isBlank() || a.startsWith("VID-") || a.startsWith("VID_") || a.matches(Regex("[0-9a-fA-F-]{20,}")))
                "Ders videosu – ${tarihYaz(System.currentTimeMillis())}"
            else a
        }
    }
}
