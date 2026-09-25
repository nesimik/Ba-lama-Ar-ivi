package com.baglamaarsivim.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.baglamaarsivim.BaglamaApp
import com.baglamaarsivim.data.KaynakTipi
import com.baglamaarsivim.util.mimedenUzanti
import com.baglamaarsivim.util.uzanti
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

object Bildirimler {
    const val KANAL = "indirmeler"

    fun kanalOlustur(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(KANAL, "Video indirmeleri", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}

/**
 * Link'ten video indirme. Ön plan (foreground) worker olarak çalışır; böylece uzun indirmeler
 * sistem tarafından durdurulmaz ve bildirimde ilerleme görünür.
 */
class IndirmeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(ANAHTAR_URL) ?: return hata("Link bulunamadı")
        val turkuId = inputData.getLong(ANAHTAR_TURKU, -1L)
        if (turkuId < 0) return hata("Türkü seçilmedi")
        val repo = (applicationContext as BaglamaApp).repo
        val bildirimId = id.hashCode()

        runCatching { setForeground(onPlanBilgisi(bildirimId, 0, true)) } // izin verilmezse sessizce devam

        val gecici = File(repo.gelenDir, "indirme_$id.tmp")
        return try {
            val baglanti = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BaglamaArsivim")
            }
            baglanti.connect()
            val kod = baglanti.responseCode
            if (kod !in 200..299) return hata("Sunucu hatası ($kod). Link süresi dolmuş olabilir.")
            val tur = baglanti.contentType?.lowercase() ?: ""
            if (tur.startsWith("text/html")) {
                return hata("Bu link bir video dosyası değil, bir web sayfası. WhatsApp videoları için Paylaş menüsünü kullanın.")
            }
            val toplam = baglanti.contentLengthLong
            var sonGuncelleme = 0L
            var inen = 0L
            baglanti.inputStream.use { girdi ->
                gecici.outputStream().use { cikti ->
                    val tampon = ByteArray(128 * 1024)
                    while (true) {
                        if (isStopped) {
                            gecici.delete(); return Result.failure(workDataOf(ANAHTAR_HATA to "İptal edildi"))
                        }
                        val n = girdi.read(tampon)
                        if (n < 0) break
                        cikti.write(tampon, 0, n)
                        inen += n
                        val simdi = System.currentTimeMillis()
                        if (simdi - sonGuncelleme > 500) {
                            sonGuncelleme = simdi
                            val oran = if (toplam > 0) inen.toFloat() / toplam else -1f
                            setProgress(workDataOf(ANAHTAR_ILERLEME to oran, ANAHTAR_INEN to inen))
                            runCatching {
                                setForeground(onPlanBilgisi(bildirimId, if (oran >= 0) (oran * 100).toInt() else 0, oran < 0))
                            }
                        }
                    }
                }
            }
            if (inen < 1024) return hata("İndirilen dosya boş ya da geçersiz")

            val yol = Uri.parse(url).lastPathSegment?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: "video"
            val ext = uzanti(yol).takeIf { it.length in 2..4 } ?: mimedenUzanti(tur.substringBefore(';')) ?: "mp4"
            val ad = if (uzanti(yol) == ext) yol else "$yol.$ext"
            repo.videoKaydet(turkuId, gecici, ad, KaynakTipi.LINK)
            Result.success()
        } catch (e: Exception) {
            gecici.delete()
            hata("İndirme başarısız: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    private fun hata(mesaj: String): Result = Result.failure(workDataOf(ANAHTAR_HATA to mesaj))

    private fun onPlanBilgisi(bildirimId: Int, yuzde: Int, belirsiz: Boolean): ForegroundInfo {
        val bildirim: Notification = NotificationCompat.Builder(applicationContext, Bildirimler.KANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Video indiriliyor")
            .setContentText(if (belirsiz) "İndiriliyor…" else "%$yuzde")
            .setProgress(100, yuzde, belirsiz)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(bildirimId, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(bildirimId, bildirim)
        }
    }

    companion object {
        const val ETIKET = "indirme"
        const val ANAHTAR_URL = "url"
        const val ANAHTAR_TURKU = "turkuId"
        const val ANAHTAR_ILERLEME = "ilerleme"
        const val ANAHTAR_INEN = "inen"
        const val ANAHTAR_HATA = "hata"

        fun turkuEtiketi(turkuId: Long) = "turku_$turkuId"

        fun baslat(ctx: Context, url: String, turkuId: Long) {
            val istek = OneTimeWorkRequestBuilder<IndirmeWorker>()
                .setInputData(workDataOf(ANAHTAR_URL to url, ANAHTAR_TURKU to turkuId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(ETIKET)
                .addTag(turkuEtiketi(turkuId))
                .build()
            WorkManager.getInstance(ctx).enqueue(istek)
        }
    }
}
