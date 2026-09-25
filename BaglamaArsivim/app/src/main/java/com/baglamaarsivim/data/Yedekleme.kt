package com.baglamaarsivim.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.baglamaarsivim.util.SayacliAkis
import com.baglamaarsivim.util.boyutYaz
import com.baglamaarsivim.util.uriBilgisi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manuel yedekleme / geri yükleme.
 *
 * Yedek tek bir .zip dosyasıdır: önce "arsiv.json" (türküler, sıralar, favoriler, notlar,
 * doküman-türkü ilişkileri, çizim katmanları), ardından "videolar/..." ve "dokumanlar/..." dosyaları.
 *
 * Kaydetme konumunu kullanıcı Android'in dosya kaydetme ekranında seçer. Orada "Drive" seçilirse
 * yedek doğrudan Google Drive'a gider — ek bir Google girişi ya da API anahtarı gerekmez.
 * Otomatik/arka plan senkronu YOKTUR; her yedek kullanıcı tetiklemelidir.
 */
class Yedekleme(private val ctx: Context, private val repo: ArsivRepository) {
    private val dao = repo.dao

    suspend fun disaAktar(hedef: Uri, turkuId: Long?, ilerleme: (Float) -> Unit): String =
        withContext(Dispatchers.IO) {
            val turkuler = if (turkuId == null) dao.tumTurkuler() else listOfNotNull(dao.turku(turkuId))
            val turkuIdleri = turkuler.map { it.id }.toSet()
            val videolar = dao.tumVideolar().filter { it.turkuId in turkuIdleri }
            val dokumanlar = dao.tumDokumanlar().filter { turkuId == null || it.turkuId == turkuId }
            val dokIdleri = dokumanlar.map { it.id }.toSet()
            val isaretler = dao.tumIsaretler().filter { it.dokumanId in dokIdleri }
            val simdi = System.currentTimeMillis()

            val json = JSONObject()
                .put("uygulama", "BaglamaArsivim")
                .put("surum", 1)
                .put("olusturma", simdi)
                .put("turkuler", JSONArray().apply {
                    turkuler.forEach { t ->
                        put(JSONObject().put("id", t.id).put("ad", t.ad).put("eklenmeTarihi", t.eklenmeTarihi)
                            .put("favori", t.favori).put("ozelSiraNo", t.ozelSiraNo)
                            .put("notMetni", t.notMetni ?: JSONObject.NULL))
                    }
                })
                .put("videolar", JSONArray().apply {
                    videolar.forEach { v ->
                        put(JSONObject().put("turkuId", v.turkuId).put("baslik", v.baslik).put("dosyaAdi", v.dosyaAdi)
                            .put("kaynakTipi", v.kaynakTipi.name).put("eklenmeTarihi", v.eklenmeTarihi)
                            .put("boyutByte", v.boyutByte).put("sonKonumMs", v.sonKonumMs)
                            .put("sonIzlenme", v.sonIzlenme ?: JSONObject.NULL))
                    }
                })
                .put("dokumanlar", JSONArray().apply {
                    dokumanlar.forEach { d ->
                        put(JSONObject().put("id", d.id).put("turkuId", d.turkuId ?: JSONObject.NULL)
                            .put("dosyaAdi", d.dosyaAdi).put("dosya", d.dosya).put("tip", d.tip.name)
                            .put("eklenmeTarihi", d.eklenmeTarihi).put("boyutByte", d.boyutByte))
                    }
                })
                .put("isaretler", JSONArray().apply {
                    isaretler.forEach { i ->
                        put(JSONObject().put("dokumanId", i.dokumanId).put("sayfaNo", i.sayfaNo)
                            .put("vektorVerisi", i.vektorVerisi).put("guncellemeTarihi", i.guncellemeTarihi))
                    }
                })

            val dosyalar: List<Pair<String, File>> =
                videolar.map { "videolar/${it.dosyaAdi}" to repo.videoDosya(it) }.filter { it.second.exists() } +
                    dokumanlar.map { "dokumanlar/${it.dosya}" to repo.dokDosya(it) }.filter { it.second.exists() }
            val toplam = dosyalar.sumOf { it.second.length() }.coerceAtLeast(1)
            var yazilan = 0L
            var sonBildirim = 0L

            val akis = ctx.contentResolver.openOutputStream(hedef) ?: error("Yedek dosyası oluşturulamadı")
            akis.use { os ->
                ZipOutputStream(BufferedOutputStream(os, 256 * 1024)).use { zip ->
                    zip.setLevel(Deflater.BEST_SPEED) // videolar zaten sıkıştırılmış, hız öncelikli
                    zip.putNextEntry(ZipEntry("arsiv.json"))
                    zip.write(json.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    val tampon = ByteArray(256 * 1024)
                    for ((ad, f) in dosyalar) {
                        zip.putNextEntry(ZipEntry(ad))
                        f.inputStream().use { girdi ->
                            while (true) {
                                val n = girdi.read(tampon)
                                if (n < 0) break
                                zip.write(tampon, 0, n)
                                yazilan += n
                                val t = System.currentTimeMillis()
                                if (t - sonBildirim > 250) {
                                    sonBildirim = t
                                    ilerleme(yazilan.toFloat() / toplam)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }

            if (turkuId == null) {
                dao.tumVideolarYedeklendi(simdi)
                dao.tumDokumanlarYedeklendi(simdi)
            } else {
                dao.turkuVideolariYedeklendi(turkuId, simdi)
                dao.turkuDokumanlariYedeklendi(turkuId, simdi)
            }
            "Yedek kaydedildi: ${turkuler.size} türkü, ${videolar.size} video, ${dokumanlar.size} doküman (${boyutYaz(toplam)})"
        }

    /**
     * @param degistir true: mevcut arşiv tamamen silinip yedekle değiştirilir.
     *                 false: birleştirilir (aynı adlı türküler eşleşir, zaten olan dosyalar atlanır).
     */
    suspend fun iceAktar(kaynak: Uri, degistir: Boolean, ilerleme: (Float) -> Unit): String =
        withContext(Dispatchers.IO) {
            val toplam = uriBilgisi(ctx, kaynak).boyut
            val sayacli = SayacliAkis(ctx.contentResolver.openInputStream(kaynak) ?: error("Dosya açılamadı"))
            var eklenenTurku = 0
            var eklenenVideo = 0
            var eklenenDok = 0
            var eksik = 0

            ZipInputStream(BufferedInputStream(sayacli, 256 * 1024)).use { zip ->
                val ilk = zip.nextEntry ?: error("Yedek dosyası boş")
                if (ilk.name != "arsiv.json") error("Bu dosya bir Bağlama Arşivim yedeği değil")
                val json = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                if (json.optString("uygulama") != "BaglamaArsivim") error("Bu dosya bir Bağlama Arşivim yedeği değil")
                val yedekTarihi = json.optLong("olusturma", System.currentTimeMillis())

                if (degistir) repo.herSeyiSil()

                // 1) Dosyaları çıkar (zaten var olanlar atlanır)
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val ad = File(e.name).name // klasör atlatma saldırılarına karşı sadece dosya adı
                    val hedef = when (e.name.substringBefore('/')) {
                        "videolar" -> File(repo.videoDir, ad)
                        "dokumanlar" -> File(repo.dokDir, ad)
                        else -> null
                    }
                    if (hedef != null && !hedef.exists()) {
                        val gecici = File(hedef.parentFile, "$ad.yukleniyor")
                        gecici.outputStream().use { zip.copyTo(it, 256 * 1024) }
                        gecici.renameTo(hedef)
                    }
                    if (toplam > 0) ilerleme((sayacli.sayac.toFloat() / toplam).coerceIn(0f, 0.99f))
                }

                // 2) Veritabanını kur
                repo.db.withTransaction {
                    val mevcutVideolar = dao.tumVideolar().map { it.dosyaAdi }.toHashSet()
                    val mevcutDoklar = dao.tumDokumanlar().map { it.dosya }.toHashSet()
                    val turkuEslesme = HashMap<Long, Long>()
                    var sira = dao.maxSira()

                    val tj = json.getJSONArray("turkuler")
                    val turkuListesi = (0 until tj.length()).map { tj.getJSONObject(it) }
                        .sortedBy { it.optInt("ozelSiraNo") }
                    for (o in turkuListesi) {
                        val ad = o.getString("ad")
                        val mevcut = if (degistir) null else dao.turkuAdla(ad)
                        turkuEslesme[o.getLong("id")] = mevcut?.id ?: run {
                            eklenenTurku++
                            dao.turkuEkle(
                                Turku(
                                    ad = ad, eklenmeTarihi = o.optLong("eklenmeTarihi"),
                                    favori = o.optBoolean("favori"), ozelSiraNo = ++sira,
                                    notMetni = o.strVeyaNull("notMetni")
                                )
                            )
                        }
                    }

                    val vj = json.getJSONArray("videolar")
                    for (i in 0 until vj.length()) {
                        val o = vj.getJSONObject(i)
                        val dosyaAdi = o.getString("dosyaAdi")
                        if (dosyaAdi in mevcutVideolar) continue
                        if (!File(repo.videoDir, dosyaAdi).exists()) { eksik++; continue }
                        val tid = turkuEslesme[o.getLong("turkuId")] ?: continue
                        dao.videoEkle(
                            Video(
                                turkuId = tid, baslik = o.getString("baslik"), dosyaAdi = dosyaAdi,
                                kaynakTipi = runCatching { KaynakTipi.valueOf(o.getString("kaynakTipi")) }.getOrDefault(KaynakTipi.DOSYA),
                                eklenmeTarihi = o.optLong("eklenmeTarihi"), boyutByte = o.optLong("boyutByte"),
                                sonKonumMs = o.optLong("sonKonumMs"), yedekTarihi = yedekTarihi,
                                sonIzlenme = if (o.isNull("sonIzlenme")) null else o.getLong("sonIzlenme")
                            )
                        )
                        eklenenVideo++
                    }

                    val dokEslesme = HashMap<Long, Long>()
                    val dj = json.getJSONArray("dokumanlar")
                    for (i in 0 until dj.length()) {
                        val o = dj.getJSONObject(i)
                        val dosya = o.getString("dosya")
                        if (dosya in mevcutDoklar) continue
                        if (!File(repo.dokDir, dosya).exists()) { eksik++; continue }
                        val eskiTurku = if (o.isNull("turkuId")) null else o.getLong("turkuId")
                        val yeniId = dao.dokumanEkle(
                            Dokuman(
                                turkuId = eskiTurku?.let { turkuEslesme[it] }, dosyaAdi = o.getString("dosyaAdi"),
                                dosya = dosya,
                                tip = runCatching { DokumanTipi.valueOf(o.getString("tip")) }.getOrDefault(DokumanTipi.PDF),
                                eklenmeTarihi = o.optLong("eklenmeTarihi"), boyutByte = o.optLong("boyutByte"),
                                yedekTarihi = yedekTarihi
                            )
                        )
                        dokEslesme[o.getLong("id")] = yeniId
                        eklenenDok++
                    }

                    val ij = json.optJSONArray("isaretler") ?: JSONArray()
                    for (i in 0 until ij.length()) {
                        val o = ij.getJSONObject(i)
                        val dokId = dokEslesme[o.getLong("dokumanId")] ?: continue
                        dao.isaretKaydet(
                            Isaret(
                                dokumanId = dokId, sayfaNo = o.getInt("sayfaNo"),
                                vektorVerisi = o.getString("vektorVerisi"),
                                guncellemeTarihi = o.optLong("guncellemeTarihi", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
            ilerleme(1f)
            buildString {
                append("Geri yükleme tamamlandı: $eklenenTurku yeni türkü, $eklenenVideo video, $eklenenDok doküman")
                if (eksik > 0) append(" ($eksik dosya yedekte bulunamadı)")
            }
        }

    private fun JSONObject.strVeyaNull(k: String): String? = if (isNull(k)) null else getString(k)
}
