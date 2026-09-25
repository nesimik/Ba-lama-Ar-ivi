package com.baglamaarsivim.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.baglamaarsivim.BaglamaApp
import com.baglamaarsivim.data.ArsivRepository
import com.baglamaarsivim.data.Dokuman
import com.baglamaarsivim.data.DokumanTipi
import com.baglamaarsivim.data.KaynakTipi
import com.baglamaarsivim.data.Turku
import com.baglamaarsivim.data.TurkuOzet
import com.baglamaarsivim.data.Video
import com.baglamaarsivim.data.Yedekleme
import com.baglamaarsivim.util.TR
import com.baglamaarsivim.util.kopyala
import com.baglamaarsivim.util.mimedenUzanti
import com.baglamaarsivim.util.tarihSaatYaz
import com.baglamaarsivim.util.uriBilgisi
import com.baglamaarsivim.util.uzanti
import com.baglamaarsivim.work.IndirmeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.Collator
import java.util.Collections
import java.util.UUID

enum class Siralama(val etiket: String) {
    TARIH_YENI("Tarih (yeni → eski)"),
    TARIH_ESKI("Tarih (eski → yeni)"),
    AZ("Alfabetik (A → Z)"),
    ZA("Alfabetik (Z → A)"),
    OZEL("Özel sıra")
}

data class GelenDosya(val dosya: File, val ad: String, val video: Boolean, val dokTipi: DokumanTipi?)
data class GelenPaket(
    val dosyalar: List<GelenDosya> = emptyList(),
    val ilerleme: Float = 0f,
    val hazir: Boolean = false,
    val desteklenmeyen: Int = 0
)

data class Islem(val baslik: String, val ilerleme: Float?)

/** YouTube ve benzeri telifli platformlardan indirme desteklenmez. */
private val ENGELLI_ALANLAR = listOf(
    "youtube.com", "youtu.be", "youtube-nocookie.com", "tiktok.com", "instagram.com", "facebook.com",
    "fb.watch", "vimeo.com", "dailymotion.com", "twitter.com", "x.com", "twitch.tv"
)

class ArsivViewModel(app: Application) : AndroidViewModel(app) {
    val repo: ArsivRepository = (app as BaglamaApp).repo
    private val dao = repo.dao
    private val prefs = app.getSharedPreferences("ayarlar", Context.MODE_PRIVATE)
    private val yedekleme = Yedekleme(app, repo)
    private val collator: Collator = Collator.getInstance(TR)

    val arama = MutableStateFlow("")
    val siralama = MutableStateFlow(
        runCatching { Siralama.valueOf(prefs.getString("siralama", null) ?: "") }.getOrDefault(Siralama.TARIH_YENI)
    )
    val izgara = MutableStateFlow(prefs.getBoolean("izgara", false))
    val siraDuzenle = MutableStateFlow(false)
    val sonYedek = MutableStateFlow(prefs.getLong("sonYedek", 0L))

    val turkuListesi: StateFlow<List<TurkuOzet>> =
        combine(dao.turkuOzetleri(), arama, siralama) { liste, q, s ->
            val aranan = q.trim().lowercase(TR)
            val filtre = if (aranan.isEmpty()) liste
            else liste.filter { it.turku.ad.lowercase(TR).contains(aranan) }
            when (s) {
                Siralama.TARIH_YENI -> filtre.sortedByDescending { it.turku.eklenmeTarihi }
                Siralama.TARIH_ESKI -> filtre.sortedBy { it.turku.eklenmeTarihi }
                Siralama.AZ -> filtre.sortedWith(compareBy(collator) { it.turku.ad })
                Siralama.ZA -> filtre.sortedWith(compareBy(collator) { it.turku.ad }).reversed()
                Siralama.OZEL -> filtre.sortedWith(compareBy({ it.turku.ozelSiraNo }, { it.turku.id }))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val turkuler: StateFlow<List<Turku>> =
        dao.turkulerFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dokumanlar = dao.dokumanOzetleri().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val yedeksizSayisi: StateFlow<Int> = combine(dao.yedeksizVideo(), dao.yedeksizDokuman()) { a, b -> a + b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val indirmeler: StateFlow<List<WorkInfo>> = WorkManager.getInstance(app)
        .getWorkInfosByTagFlow(IndirmeWorker.ETIKET)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val gizlenenIndirmeler = MutableStateFlow(setOf<UUID>())

    val gelen = MutableStateFlow<GelenPaket?>(null)
    private var gelenIsi: Job? = null
    val islem = MutableStateFlow<Islem?>(null)
    val mesajlar = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun mesaj(s: String) {
        mesajlar.tryEmit(s)
    }

    // ---------- Liste ayarları ----------
    fun siralamaSec(s: Siralama) {
        siralama.value = s
        prefs.edit().putString("siralama", s.name).apply()
        if (s != Siralama.OZEL) siraDuzenle.value = false
    }

    fun izgaraDegistir() {
        izgara.value = !izgara.value
        prefs.edit().putBoolean("izgara", izgara.value).apply()
    }

    // ---------- Türkü ----------
    fun turkuOlustur(ad: String, sonra: (Long) -> Unit = {}) {
        if (ad.isBlank()) return
        viewModelScope.launch { sonra(repo.yeniTurku(ad)) }
    }

    fun favori(t: Turku) = viewModelScope.launch { dao.turkuGuncelle(t.copy(favori = !t.favori)) }
    fun turkuGuncelle(t: Turku) = viewModelScope.launch { dao.turkuGuncelle(t) }
    fun turkuSil(t: Turku, dokumanlarDa: Boolean) = viewModelScope.launch {
        repo.turkuSil(t, dokumanlarDa)
        mesaj("\"${t.ad}\" silindi")
    }

    /** Özel sıralamada iki türkünün yerini değiştirir ve tüm sıra numaralarını yeniden düzenler. */
    fun yerDegistir(a: Turku, b: Turku) = viewModelScope.launch {
        val tum = dao.tumTurkuler().sortedWith(compareBy({ it.ozelSiraNo }, { it.id })).toMutableList()
        val i = tum.indexOfFirst { it.id == a.id }
        val j = tum.indexOfFirst { it.id == b.id }
        if (i < 0 || j < 0) return@launch
        Collections.swap(tum, i, j)
        dao.turkulerGuncelle(tum.mapIndexed { k, t -> t.copy(ozelSiraNo = k) })
    }

    // ---------- Uzun işlemler ----------
    private fun islemli(baslik: String, blok: suspend ((Float) -> Unit) -> Unit) {
        viewModelScope.launch {
            islem.value = Islem(baslik, null)
            try {
                blok { p -> islem.value = Islem(baslik, p) }
            } catch (e: Exception) {
                mesaj("Hata: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                islem.value = null
            }
        }
    }

    // ---------- Video ----------
    fun videolariEkle(turkuId: Long, uriler: List<Uri>) {
        if (uriler.isEmpty()) return
        islemli("Videolar arşive kopyalanıyor") { ilerle ->
            uriler.forEachIndexed { i, u -> repo.uridenVideo(turkuId, u) { p -> ilerle((i + p) / uriler.size) } }
            mesaj("${uriler.size} video eklendi")
        }
    }

    fun linkIndir(url: String, turkuId: Long): Boolean {
        val u = url.trim()
        val host = runCatching { Uri.parse(u).host?.lowercase() }.getOrNull()
        if (host.isNullOrEmpty() || !(u.startsWith("http://") || u.startsWith("https://"))) {
            mesaj("Geçerli bir link değil"); return false
        }
        if (ENGELLI_ALANLAR.any { host == it || host.endsWith(".$it") }) {
            mesaj("Bu platformdan video indirme desteklenmiyor (telif / kullanım koşulları)."); return false
        }
        IndirmeWorker.baslat(getApplication(), u, turkuId)
        mesaj("İndirme başladı — ilerlemeyi bildirimden ve türkü sayfasından izleyebilirsin")
        return true
    }

    fun indirmeyiIptal(id: UUID) {
        WorkManager.getInstance(getApplication()).cancelWorkById(id)
    }

    fun indirmeyiGizle(id: UUID) {
        gizlenenIndirmeler.value = gizlenenIndirmeler.value + id
    }

    fun videoGuncelle(v: Video) = viewModelScope.launch { dao.videoGuncelle(v) }
    fun videoSil(v: Video) = viewModelScope.launch { repo.videoSil(v) }
    fun konumKaydet(id: Long, ms: Long) = viewModelScope.launch { dao.konumKaydet(id, ms, System.currentTimeMillis()) }

    val sonIzlenen = dao.sonIzlenen().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Son seçilen çalma hızı videolar arasında korunur (pratikte aynı tempoyla devam etmek için). */
    var sonHiz: Float
        get() = prefs.getFloat("hiz", 1f)
        set(v) { prefs.edit().putFloat("hiz", v).apply() }

    // ---------- Doküman ----------
    fun dokumanlariEkle(turkuId: Long?, uriler: List<Uri>) {
        if (uriler.isEmpty()) return
        islemli("Dokümanlar ekleniyor") { ilerle ->
            var basarili = 0
            uriler.forEachIndexed { i, u ->
                if (repo.uridenDokuman(turkuId, u)) basarili++
                ilerle((i + 1f) / uriler.size)
            }
            val red = uriler.size - basarili
            mesaj(if (red == 0) "$basarili doküman eklendi" else "$basarili doküman eklendi, $red dosya desteklenmiyor (PDF, resim, Word)")
        }
    }

    fun taramaEkle(turkuId: Long?, dosya: File) = viewModelScope.launch {
        if (!dosya.exists() || dosya.length() == 0L) return@launch
        repo.dokumanKaydet(turkuId, dosya, "Tarama ${tarihSaatYaz(System.currentTimeMillis())}.jpg", DokumanTipi.IMAGE)
        mesaj("Tarama eklendi")
    }

    fun dokumanGuncelle(d: Dokuman) = viewModelScope.launch { dao.dokumanGuncelle(d) }
    fun dokumanSil(d: Dokuman) = viewModelScope.launch { repo.dokumanSil(d) }

    // ---------- Paylaş menüsünden gelen dosyalar ----------
    fun paylasimAl(intent: Intent) {
        val uriler = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uriler += it }
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uriler += it }
        }
        if (uriler.isEmpty()) {
            intent.clipData?.let { c -> for (i in 0 until c.itemCount) c.getItemAt(i).uri?.let { uriler += it } }
        }
        if (uriler.isEmpty()) {
            mesaj("Paylaşılan dosya bulunamadı"); return
        }
        paylasimIptal()
        gelen.value = GelenPaket()
        val varsayilanTip = intent.type
        val ctx = getApplication<Application>()
        // Paylaşımla gelen URI izni geçicidir: dosyalar HEMEN uygulamaya kopyalanır.
        gelenIsi = viewModelScope.launch(Dispatchers.IO) {
            val bilgiler = uriler.map { it to uriBilgisi(ctx, it, varsayilanTip) }
            val toplam = bilgiler.sumOf { maxOf(it.second.boyut, 0L) }.coerceAtLeast(1L)
            var biten = 0L
            var desteksiz = 0
            val liste = mutableListOf<GelenDosya>()
            for ((uri, b) in bilgiler) {
                if (!isActive) break
                val video = ArsivRepository.videoMu(b.mime, b.ad)
                val dt = if (video) null else ArsivRepository.dokumanTipi(b.mime, b.ad)
                if (!video && dt == null) { desteksiz++; continue }
                val ext = uzanti(b.ad).ifEmpty { mimedenUzanti(b.mime) ?: "" }
                val ad = if (uzanti(b.ad).isEmpty() && ext.isNotEmpty()) "${b.ad}.$ext" else b.ad
                val hedef = File(repo.gelenDir, UUID.randomUUID().toString() + if (ext.isNotEmpty()) ".$ext" else "")
                val boyut = maxOf(b.boyut, 0L)
                val basarili = try {
                    val akis = ctx.contentResolver.openInputStream(uri)
                    if (akis == null) false else {
                        akis.use { i ->
                            hedef.outputStream().use { o ->
                                kopyala(i, o, b.boyut) { p ->
                                    if (isActive) gelen.value = gelen.value?.copy(ilerleme = (biten + p * boyut) / toplam.toFloat())
                                }
                            }
                        }
                        true
                    }
                } catch (e: Exception) {
                    false
                }
                if (basarili) {
                    liste += GelenDosya(hedef, ad, video, dt)
                    biten += boyut
                } else {
                    hedef.delete(); desteksiz++
                }
            }
            if (!isActive) {
                liste.forEach { it.dosya.delete() }
            } else if (liste.isEmpty()) {
                gelen.value = null
                mesaj("Paylaşılan dosyalar desteklenmiyor veya okunamadı")
            } else {
                gelen.value = GelenPaket(liste, 1f, true, desteksiz)
            }
        }
    }

    fun paylasimiKaydet(turkuId: Long?, sonra: (Long?) -> Unit) {
        val paket = gelen.value ?: return
        viewModelScope.launch {
            var video = 0
            var dok = 0
            for (g in paket.dosyalar) {
                if (g.video) {
                    if (turkuId != null) { repo.videoKaydet(turkuId, g.dosya, g.ad, KaynakTipi.PAYLASIM); video++ }
                } else if (g.dokTipi != null) {
                    repo.dokumanKaydet(turkuId, g.dosya, g.ad, g.dokTipi); dok++
                }
            }
            gelen.value = null
            mesaj(listOfNotNull(
                if (video > 0) "$video video" else null,
                if (dok > 0) "$dok doküman" else null
            ).joinToString(", ") + " arşive eklendi")
            sonra(turkuId)
        }
    }

    fun paylasimIptal() {
        gelenIsi?.cancel()
        gelenIsi = null
        gelen.value?.dosyalar?.forEach { it.dosya.delete() }
        gelen.value = null
    }

    // ---------- Yedekleme ----------
    fun yedekle(hedef: Uri, turkuId: Long?) {
        islemli("Yedek oluşturuluyor") { p ->
            val sonuc = yedekleme.disaAktar(hedef, turkuId, p)
            if (turkuId == null) {
                val simdi = System.currentTimeMillis()
                prefs.edit().putLong("sonYedek", simdi).apply()
                sonYedek.value = simdi
            }
            mesaj(sonuc)
        }
    }

    fun geriYukle(kaynak: Uri, degistir: Boolean) {
        islemli("Yedekten geri yükleniyor") { p -> mesaj(yedekleme.iceAktar(kaynak, degistir, p)) }
    }
}
