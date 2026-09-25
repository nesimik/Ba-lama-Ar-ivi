package com.baglamaarsivim.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.baglamaarsivim.util.resimYukle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import kotlin.math.roundToLong

/** Tek bir serbest çizgi. Noktalar sayfa boyutuna göre normalize (0..1); kalınlık sayfa genişliğine oranlı. */
data class Cizgi(val noktalar: List<Pair<Float, Float>>, val renk: Int, val kalinlik: Float)

/** Sayfaya bırakılmış metin notu. Konum normalize, boyut sayfa genişliğine oranlı. */
data class MetinNotu(val x: Float, val y: Float, val metin: String, val renk: Int, val boyut: Float)

/** Bir sayfanın çizim/not katmanı. Orijinal dokümandan tamamen ayrı saklanır. */
data class Katman(val cizgiler: List<Cizgi> = emptyList(), val metinler: List<MetinNotu> = emptyList()) {
    val bos: Boolean get() = cizgiler.isEmpty() && metinler.isEmpty()

    fun json(): String {
        fun y(f: Float) = (f * 10000).roundToLong() / 10000.0
        val c = JSONArray()
        cizgiler.forEach { z ->
            val p = JSONArray()
            z.noktalar.forEach { (px, py) -> p.put(y(px)); p.put(y(py)) }
            c.put(JSONObject().put("r", z.renk).put("k", y(z.kalinlik)).put("p", p))
        }
        val m = JSONArray()
        metinler.forEach { t ->
            m.put(JSONObject().put("x", y(t.x)).put("y", y(t.y)).put("t", t.metin).put("r", t.renk).put("b", y(t.boyut)))
        }
        return JSONObject().put("c", c).put("m", m).toString()
    }

    companion object {
        fun jsondan(s: String): Katman = runCatching {
            val o = JSONObject(s)
            val c = o.optJSONArray("c") ?: JSONArray()
            val cizgiler = (0 until c.length()).map { i ->
                val z = c.getJSONObject(i)
                val p = z.getJSONArray("p")
                val noktalar = (0 until p.length() / 2).map { j ->
                    p.getDouble(j * 2).toFloat() to p.getDouble(j * 2 + 1).toFloat()
                }
                Cizgi(noktalar, z.getInt("r"), z.getDouble("k").toFloat())
            }
            val m = o.optJSONArray("m") ?: JSONArray()
            val metinler = (0 until m.length()).map { i ->
                val t = m.getJSONObject(i)
                MetinNotu(t.getDouble("x").toFloat(), t.getDouble("y").toFloat(), t.getString("t"),
                    t.getInt("r"), t.getDouble("b").toFloat())
            }
            Katman(cizgiler, metinler)
        }.getOrDefault(Katman())
    }
}

/** Katmanı herhangi bir Android Canvas'a çizer — hem ekranda hem dışa aktarmada aynı kod kullanılır. */
object KatmanCizici {
    private val kalem = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val dolgu = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val yaziKenar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeJoin = Paint.Join.ROUND
    }
    private val yazi = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun ciz(canvas: Canvas, k: Katman, w: Float, h: Float, aktif: Cizgi? = null) {
        (k.cizgiler + listOfNotNull(aktif)).forEach { cizgiCiz(canvas, it, w, h) }
        k.metinler.forEach { t ->
            val boyut = t.boyut * w
            yazi.color = t.renk; yazi.textSize = boyut
            yaziKenar.textSize = boyut; yaziKenar.strokeWidth = boyut * 0.18f
            t.metin.split('\n').forEachIndexed { i, satir ->
                val yy = t.y * h + i * boyut * 1.2f
                canvas.drawText(satir, t.x * w, yy, yaziKenar)
                canvas.drawText(satir, t.x * w, yy, yazi)
            }
        }
    }

    private fun cizgiCiz(canvas: Canvas, c: Cizgi, w: Float, h: Float) {
        if (c.noktalar.isEmpty()) return
        val kalinlik = (c.kalinlik * w).coerceAtLeast(1f)
        if (c.noktalar.size == 1) {
            dolgu.color = c.renk
            val (x, y) = c.noktalar[0]
            canvas.drawCircle(x * w, y * h, kalinlik / 2, dolgu)
            return
        }
        kalem.color = c.renk; kalem.strokeWidth = kalinlik
        val yol = Path()
        c.noktalar.forEachIndexed { i, (x, y) -> if (i == 0) yol.moveTo(x * w, y * h) else yol.lineTo(x * w, y * h) }
        canvas.drawPath(yol, kalem)
    }
}

/** PDF'i (PdfRenderer ile) veya resmi sayfa sayfa bitmap olarak veren kaynak. */
class SayfaKaynagi private constructor(
    private val pfd: ParcelFileDescriptor?,
    private val pdf: PdfRenderer?,
    private val resim: File?
) {
    private var kapali = false
    val sayfaSayisi: Int = pdf?.pageCount ?: 1

    @Synchronized
    fun render(sayfa: Int, genislik: Int): Bitmap? {
        if (kapali) return null
        return runCatching {
            if (pdf != null) {
                val s = pdf.openPage(sayfa.coerceIn(0, sayfaSayisi - 1))
                try {
                    val yukseklik = (genislik.toFloat() * s.height / s.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(genislik, yukseklik, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    s.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally {
                    s.close()
                }
            } else {
                resimYukle(resim!!, 2400)
            }
        }.getOrNull()
    }

    @Synchronized
    fun kapat() {
        if (kapali) return
        kapali = true
        runCatching { pdf?.close() }
        runCatching { pfd?.close() }
    }

    companion object {
        fun ac(f: File, tip: DokumanTipi): SayfaKaynagi =
            if (tip == DokumanTipi.PDF) {
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    SayfaKaynagi(pfd, PdfRenderer(pfd), null)
                } catch (e: Exception) {
                    pfd.close(); throw e
                }
            } else {
                SayfaKaynagi(null, null, f)
            }
    }
}

/** İşaretlenmiş dokümanı PNG veya PDF olarak dışa aktarır. Orijinal dosyaya dokunmaz. */
object Disaaktarim {
    fun png(bmp: Bitmap, katman: Katman, cikti: OutputStream) {
        val kopya = bmp.copy(Bitmap.Config.ARGB_8888, true)
        KatmanCizici.ciz(Canvas(kopya), katman, kopya.width.toFloat(), kopya.height.toFloat())
        kopya.compress(Bitmap.CompressFormat.PNG, 100, cikti)
        kopya.recycle()
    }

    fun pdf(dosya: File, tip: DokumanTipi, katmanlar: Map<Int, Katman>, cikti: OutputStream) {
        val kaynak = SayfaKaynagi.ac(dosya, tip)
        val belge = PdfDocument()
        try {
            val boya = Paint(Paint.FILTER_BITMAP_FLAG)
            for (i in 0 until kaynak.sayfaSayisi) {
                val bmp = kaynak.render(i, 1600) ?: continue
                KatmanCizici.ciz(Canvas(bmp), katmanlar[i] ?: Katman(), bmp.width.toFloat(), bmp.height.toFloat())
                val gen = 595 // A4 genişliği (pt)
                val yuk = (gen.toFloat() * bmp.height / bmp.width).toInt()
                val sayfa = belge.startPage(PdfDocument.PageInfo.Builder(gen, yuk, i + 1).create())
                sayfa.canvas.drawBitmap(bmp, null, Rect(0, 0, gen, yuk), boya)
                belge.finishPage(sayfa)
                bmp.recycle()
            }
            belge.writeTo(cikti)
        } finally {
            belge.close()
            kaynak.kapat()
        }
    }
}
