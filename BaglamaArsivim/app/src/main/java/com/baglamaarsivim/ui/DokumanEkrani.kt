package com.baglamaarsivim.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.baglamaarsivim.data.Cizgi
import com.baglamaarsivim.data.Disaaktarim
import com.baglamaarsivim.data.Dokuman
import com.baglamaarsivim.data.DokumanTipi
import com.baglamaarsivim.data.Isaret
import com.baglamaarsivim.data.Katman
import com.baglamaarsivim.data.KatmanCizici
import com.baglamaarsivim.data.MetinNotu
import com.baglamaarsivim.data.SayfaKaynagi
import com.baglamaarsivim.util.disaridaAc
import com.baglamaarsivim.util.uzantidanMime
import com.baglamaarsivim.util.uzantisiz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

private enum class Arac { GEZIN, KALEM, SILGI, METIN }

private val RENKLER = listOf(
    Color(0xFF1A1A1A), Color(0xFFD32F2F), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFEF6C00), Color(0xFF8E24AA)
)

@Composable
fun DokumanEkrani(vm: ArsivViewModel, dokumanId: Long, geri: () -> Unit) {
    val dokuman by produceState<Dokuman?>(null, dokumanId) { value = vm.repo.dao.dokuman(dokumanId) }
    val d = dokuman
    when {
        d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        d.tip == DokumanTipi.WORD -> WordEkrani(vm, d, geri)
        else -> Goruntuleyici(vm, d, geri)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordEkrani(vm: ArsivViewModel, d: Dokuman, geri: () -> Unit) {
    val ctx = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(d.dosyaAdi, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = geri) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } }
        )
    }) { ic ->
        Column(
            Modifier.padding(ic).fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Description, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(d.dosyaAdi, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Word dosyaları şimdilik uygulama içinde gösterilmiyor. Telefonundaki Word / Google Dokümanlar " +
                    "gibi bir uygulamayla açabilirsin.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                if (!disaridaAc(ctx, vm.repo.dokDosya(d), uzantidanMime(d.dosya))) {
                    vm.mesaj("Bu dosyayı açabilecek bir uygulama bulunamadı")
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Harici uygulamada aç")
            }
            // TODO (ileri faz): WebView tabanlı Word önizleme — .docx içindeki document.xml HTML'e çevrilip gösterilebilir.
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Goruntuleyici(vm: ArsivViewModel, d: Dokuman, geri: () -> Unit) {
    val ctx = LocalContext.current
    val dao = vm.repo.dao
    val kapsam = rememberCoroutineScope()
    val dosya = remember(d.id) { vm.repo.dokDosya(d) }

    val kaynak: SayfaKaynagi? = remember(d.id) { runCatching { SayfaKaynagi.ac(dosya, d.tip) }.getOrNull() }
    DisposableEffect(kaynak) { onDispose { kaynak?.kapat() } }

    if (kaynak == null) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(d.dosyaAdi) },
                navigationIcon = { IconButton(onClick = geri) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } })
        }) { ic ->
            Column(Modifier.padding(ic).fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bu dosya açılamadı (bozuk ya da şifreli olabilir).", textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { disaridaAc(ctx, dosya, uzantidanMime(d.dosya)) }) { Text("Harici uygulamada aç") }
            }
        }
        return
    }

    var sayfa by rememberSaveable { mutableIntStateOf(0) }
    val bitmap by produceState<Bitmap?>(null, sayfa) {
        value = null
        value = withContext(Dispatchers.IO) { kaynak.render(sayfa, 1800) }
    }

    // --- Çizim katmanı durumu (sayfa başına) ---
    var katman by remember { mutableStateOf(Katman()) }
    val geriAl = remember { mutableStateListOf<Katman>() }
    val yinele = remember { mutableStateListOf<Katman>() }
    var aktif by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    LaunchedEffect(d.id, sayfa) {
        katman = dao.isaret(d.id, sayfa)?.let { Katman.jsondan(it.vektorVerisi) } ?: Katman()
        geriAl.clear(); yinele.clear()
    }

    fun kaydet(k: Katman, s: Int) {
        kapsam.launch {
            if (k.bos) dao.isaretSil(d.id, s)
            else dao.isaretKaydet(Isaret(dokumanId = d.id, sayfaNo = s, vektorVerisi = k.json()))
        }
    }

    fun degistir(yeni: Katman) {
        geriAl.add(katman)
        if (geriAl.size > 60) geriAl.removeAt(0)
        yinele.clear()
        katman = yeni
        kaydet(yeni, sayfa)
    }

    var arac by remember { mutableStateOf(Arac.GEZIN) }
    var renk by remember { mutableStateOf(RENKLER[1]) }
    var kalinlik by remember { mutableFloatStateOf(4f) }
    var olcek by remember { mutableFloatStateOf(1f) }
    var kaydir by remember { mutableStateOf(Offset.Zero) }
    var metinKonumu by remember { mutableStateOf<Offset?>(null) }
    var disaMenu by remember { mutableStateOf(false) }

    val pngKaydet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bmp = bitmap
        if (uri != null && bmp != null) kapsam.launch {
            val tamam = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { Disaaktarim.png(bmp, katman, it) } != null }.getOrDefault(false)
            }
            vm.mesaj(if (tamam) "PNG kaydedildi" else "Kaydedilemedi")
        }
    }
    val pdfKaydet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) kapsam.launch {
            vm.mesaj("PDF hazırlanıyor…")
            val tamam = withContext(Dispatchers.IO) {
                runCatching {
                    val katmanlar = dao.isaretler(d.id).associate { it.sayfaNo to Katman.jsondan(it.vektorVerisi) }
                    ctx.contentResolver.openOutputStream(uri)?.use { Disaaktarim.pdf(dosya, d.tip, katmanlar, it) } != null
                }.getOrDefault(false)
            }
            vm.mesaj(if (tamam) "İşaretli PDF kaydedildi" else "PDF oluşturulamadı")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(d.dosyaAdi, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = geri) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    IconButton(enabled = geriAl.isNotEmpty(), onClick = {
                        yinele.add(katman); katman = geriAl.removeAt(geriAl.lastIndex); kaydet(katman, sayfa)
                    }) { Icon(Icons.AutoMirrored.Filled.Undo, "Geri al") }
                    IconButton(enabled = yinele.isNotEmpty(), onClick = {
                        geriAl.add(katman); katman = yinele.removeAt(yinele.lastIndex); kaydet(katman, sayfa)
                    }) { Icon(Icons.AutoMirrored.Filled.Redo, "Yinele") }
                    Box {
                        IconButton(onClick = { disaMenu = true }) { Icon(Icons.Filled.IosShare, "Dışa aktar") }
                        DropdownMenu(expanded = disaMenu, onDismissRequest = { disaMenu = false }) {
                            DropdownMenuItem(text = { Text("Bu sayfayı PNG kaydet") }, onClick = {
                                disaMenu = false; pngKaydet.launch("${uzantisiz(d.dosyaAdi)}_s${sayfa + 1}.png")
                            })
                            DropdownMenuItem(text = { Text("Tümünü işaretli PDF kaydet") }, onClick = {
                                disaMenu = false; pdfKaydet.launch("${uzantisiz(d.dosyaAdi)}_isaretli.pdf")
                            })
                            DropdownMenuItem(text = { Text("Harici uygulamada aç") }, onClick = {
                                disaMenu = false; disaridaAc(ctx, dosya, uzantidanMime(d.dosya))
                            })
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    if (arac == Arac.KALEM || arac == Arac.METIN) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 8.dp)) {
                            RENKLER.forEach { r ->
                                Box(
                                    Modifier.size(28.dp).background(r, CircleShape)
                                        .border(if (r == renk) 3.dp else 1.dp,
                                            if (r == renk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                        .clickable { renk = r }
                                )
                            }
                            Slider(value = kalinlik, onValueChange = { kalinlik = it }, valueRange = 1f..20f, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AracDugmesi(Icons.Filled.PanTool, "Gezin / yakınlaştır", arac == Arac.GEZIN) { arac = Arac.GEZIN }
                        AracDugmesi(Icons.Filled.Draw, "Kalem", arac == Arac.KALEM) { arac = Arac.KALEM }
                        AracDugmesi(Icons.Filled.CleaningServices, "Silgi", arac == Arac.SILGI) { arac = Arac.SILGI }
                        AracDugmesi(Icons.Filled.TextFields, "Metin notu", arac == Arac.METIN) { arac = Arac.METIN }
                        Spacer(Modifier.weight(1f))
                        if (kaynak.sayfaSayisi > 1) {
                            IconButton(enabled = sayfa > 0, onClick = { sayfa--; olcek = 1f; kaydir = Offset.Zero }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Önceki sayfa")
                            }
                            Text("${sayfa + 1} / ${kaynak.sayfaSayisi}", style = MaterialTheme.typography.labelLarge)
                            IconButton(enabled = sayfa < kaynak.sayfaSayisi - 1, onClick = { sayfa++; olcek = 1f; kaydir = Offset.Zero }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Sonraki sayfa")
                            }
                        }
                    }
                }
            }
        }
    ) { ic ->
        Box(
            Modifier.padding(ic).fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (arac == Arac.GEZIN) Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            olcek = (olcek * zoom).coerceIn(1f, 6f)
                            kaydir = if (olcek <= 1.01f) Offset.Zero else kaydir + pan
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp == null) {
                CircularProgressIndicator()
            } else {
                val resim = remember(bmp) { bmp.asImageBitmap() }
                Box(
                    Modifier.aspectRatio(bmp.width.toFloat() / bmp.height)
                        .graphicsLayer {
                            scaleX = olcek; scaleY = olcek
                            translationX = kaydir.x; translationY = kaydir.y
                        }
                ) {
                    Image(resim, null, Modifier.fillMaxSize())
                    val girdi: Modifier = when (arac) {
                        Arac.KALEM -> Modifier
                            .pointerInput(arac) {
                                detectDragGestures(
                                    onDragStart = { o -> aktif = listOf(normalize(o, size)) },
                                    onDragEnd = {
                                        if (aktif.isNotEmpty()) {
                                            degistir(katman.copy(cizgiler = katman.cizgiler + Cizgi(aktif, renk.toArgb(), kalinlik / 1000f)))
                                        }
                                        aktif = emptyList()
                                    },
                                    onDragCancel = { aktif = emptyList() }
                                ) { degisim, _ ->
                                    degisim.consume()
                                    aktif = aktif + normalize(degisim.position, size)
                                }
                            }
                            .pointerInput("nokta") {
                                detectTapGestures { o ->
                                    degistir(katman.copy(cizgiler = katman.cizgiler + Cizgi(listOf(normalize(o, size)), renk.toArgb(), kalinlik / 1000f)))
                                }
                            }
                        Arac.SILGI -> Modifier
                            .pointerInput(arac) {
                                val yaricap = 22.dp.toPx()
                                detectDragGestures(
                                    onDragStart = { o ->
                                        geriAl.add(katman); yinele.clear()
                                        katman = sil(katman, o, size, yaricap)
                                    },
                                    onDragEnd = { kaydet(katman, sayfa) },
                                    onDragCancel = { kaydet(katman, sayfa) }
                                ) { degisim, _ ->
                                    degisim.consume()
                                    katman = sil(katman, degisim.position, size, yaricap)
                                }
                            }
                            .pointerInput("silgiDokun") {
                                val yaricap = 22.dp.toPx()
                                detectTapGestures { o ->
                                    val yeni = sil(katman, o, size, yaricap)
                                    if (yeni != katman) degistir(yeni)
                                }
                            }
                        Arac.METIN -> Modifier.pointerInput(arac) {
                            detectTapGestures { o -> metinKonumu = Offset(o.x / size.width, o.y / size.height) }
                        }
                        Arac.GEZIN -> Modifier
                    }
                    Canvas(Modifier.fillMaxSize().then(girdi)) {
                        val etkin = if (aktif.isNotEmpty()) Cizgi(aktif, renk.toArgb(), kalinlik / 1000f) else null
                        drawIntoCanvas { c -> KatmanCizici.ciz(c.nativeCanvas, katman, size.width, size.height, etkin) }
                    }
                }
            }
        }
    }

    metinKonumu?.let { konum ->
        MetinDialog(
            baslik = "Metin notu", etiket = "Not (ör. \"burada tavır\", \"2. perde\")", onayMetni = "Ekle", cokSatirli = true,
            onKapat = { metinKonumu = null },
            onOnay = { metin ->
                degistir(katman.copy(metinler = katman.metinler + MetinNotu(konum.x, konum.y, metin, renk.toArgb(), 0.015f + kalinlik * 0.0025f)))
                metinKonumu = null
            }
        )
    }
}

@Composable
private fun AracDugmesi(ikon: androidx.compose.ui.graphics.vector.ImageVector, ad: String, secili: Boolean, onClick: () -> Unit) {
    IconToggleButton(checked = secili, onCheckedChange = { onClick() }) {
        Icon(ikon, ad, tint = if (secili) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun normalize(o: Offset, boyut: IntSize): Pair<Float, Float> =
    (o.x / boyut.width).coerceIn(0f, 1f) to (o.y / boyut.height).coerceIn(0f, 1f)

/** Dokunulan noktaya yakın çizgileri ve metin notlarını siler (nesne silgisi). */
private fun sil(k: Katman, o: Offset, boyut: IntSize, yaricap: Float): Katman {
    val w = boyut.width.toFloat()
    val h = boyut.height.toFloat()
    val cizgiler = k.cizgiler.filterNot { c ->
        val esik = yaricap + c.kalinlik * w / 2
        c.noktalar.any { (x, y) -> hypot(x * w - o.x, y * h - o.y) < esik }
    }
    val metinler = k.metinler.filterNot { m -> hypot(m.x * w - o.x, m.y * h - o.y) < yaricap * 2.5f }
    return if (cizgiler.size == k.cizgiler.size && metinler.size == k.metinler.size) k else Katman(cizgiler, metinler)
}

/**
 * Salt okunur nota önizlemesi — oynatıcı ekranında videonun altında gösterilir.
 * Kaydedilmiş çizim/notlar da görünür; iki parmakla yakınlaştırılabilir.
 */
@Composable
fun NotaOnizleme(vm: ArsivViewModel, d: Dokuman, modifier: Modifier = Modifier) {
    val dosya = remember(d.id) { vm.repo.dokDosya(d) }
    val kaynak: SayfaKaynagi? = remember(d.id) { runCatching { SayfaKaynagi.ac(dosya, d.tip) }.getOrNull() }
    DisposableEffect(kaynak) { onDispose { kaynak?.kapat() } }
    if (kaynak == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Bu dosya açılamadı") }
        return
    }
    var sayfa by rememberSaveable(d.id) { mutableIntStateOf(0) }
    val bitmap by produceState<Bitmap?>(null, d.id, sayfa) {
        value = null
        value = withContext(Dispatchers.IO) { kaynak.render(sayfa, 1600) }
    }
    val katman by produceState(Katman(), d.id, sayfa) {
        value = vm.repo.dao.isaret(d.id, sayfa)?.let { Katman.jsondan(it.vektorVerisi) } ?: Katman()
    }
    var olcek by remember(d.id, sayfa) { mutableFloatStateOf(1f) }
    var kaydir by remember(d.id, sayfa) { mutableStateOf(Offset.Zero) }

    Column(modifier) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(d.id, sayfa) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        olcek = (olcek * zoom).coerceIn(1f, 6f)
                        kaydir = if (olcek <= 1.01f) Offset.Zero else kaydir + pan
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp == null) CircularProgressIndicator()
            else {
                val resim = remember(bmp) { bmp.asImageBitmap() }
                Box(
                    Modifier.aspectRatio(bmp.width.toFloat() / bmp.height).graphicsLayer {
                        scaleX = olcek; scaleY = olcek; translationX = kaydir.x; translationY = kaydir.y
                    }
                ) {
                    Image(resim, null, Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) {
                        drawIntoCanvas { c -> KatmanCizici.ciz(c.nativeCanvas, katman, size.width, size.height) }
                    }
                }
            }
        }
        if (kaynak.sayfaSayisi > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = sayfa > 0, onClick = { sayfa-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Önceki sayfa")
                }
                Text("${sayfa + 1} / ${kaynak.sayfaSayisi}", style = MaterialTheme.typography.labelLarge)
                IconButton(enabled = sayfa < kaynak.sayfaSayisi - 1, onClick = { sayfa++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Sonraki sayfa")
                }
            }
        }
    }
}
