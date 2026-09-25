package com.baglamaarsivim.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baglamaarsivim.data.DokumanOzet
import com.baglamaarsivim.data.SonIzlenen
import com.baglamaarsivim.util.sureYaz
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CloudUpload
import com.baglamaarsivim.data.TurkuOzet
import com.baglamaarsivim.util.tarihYaz

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnaEkran(
    vm: ArsivViewModel,
    turkuAc: (Long) -> Unit,
    videoAc: (Long) -> Unit,
    dokumanAc: (DokumanOzet) -> Unit
) {
    var sekme by rememberSaveable { mutableIntStateOf(0) }
    val ekleme = rememberEklemeDurumu()

    Scaffold(
        topBar = {
            when (sekme) {
                0 -> ArsivUstCubugu(vm, "Bağlama Arşivim")
                1 -> ArsivUstCubugu(vm, "Favoriler")
                2 -> TopAppBar(title = { Text("Notalar Arşivi") })
                else -> TopAppBar(title = { Text("Ayarlar") })
            }
        },
        bottomBar = {
            NavigationBar {
                listOf<Pair<String, ImageVector>>(
                    "Arşiv" to Icons.Filled.LibraryMusic,
                    "Favoriler" to Icons.Filled.Star,
                    "Notalar" to Icons.Filled.Description,
                    "Ayarlar" to Icons.Filled.Settings
                ).forEachIndexed { i, (ad, ikon) ->
                    NavigationBarItem(
                        selected = sekme == i, onClick = { sekme = i },
                        icon = { Icon(ikon, null) }, label = { Text(ad) }
                    )
                }
            }
        },
        floatingActionButton = { if (sekme <= 2) EkleButonu(sadeceDokuman = sekme == 2, ekleme = ekleme) }
    ) { ic ->
        Box(Modifier.padding(ic).fillMaxSize()) {
            when (sekme) {
                0 -> TurkuListesi(vm, sadeceFavori = false, turkuAc = turkuAc, videoAc = videoAc, yedeklemeyeGit = { sekme = 3 })
                1 -> TurkuListesi(vm, sadeceFavori = true, turkuAc = turkuAc, videoAc = videoAc, yedeklemeyeGit = { sekme = 3 })
                2 -> NotalarEkrani(vm, dokumanAc)
                else -> AyarlarEkrani(vm)
            }
        }
    }
    EklemeAkisi(vm, ekleme, turkuAc)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArsivUstCubugu(vm: ArsivViewModel, baslik: String) {
    val arama by vm.arama.collectAsState()
    val siralama by vm.siralama.collectAsState()
    val izgara by vm.izgara.collectAsState()
    val duzenle by vm.siraDuzenle.collectAsState()
    var siralamaMenusu by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = { Text(baslik) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            actions = {
                if (siralama == Siralama.OZEL) {
                    IconButton(onClick = { vm.siraDuzenle.value = !duzenle }) {
                        Icon(if (duzenle) Icons.Filled.Check else Icons.Filled.SwapVert, "Sırayı düzenle")
                    }
                }
                IconButton(onClick = { vm.izgaraDegistir() }) {
                    Icon(if (izgara) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, "Görünüm")
                }
                Box {
                    IconButton(onClick = { siralamaMenusu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sırala") }
                    DropdownMenu(expanded = siralamaMenusu, onDismissRequest = { siralamaMenusu = false }) {
                        Siralama.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.etiket) },
                                leadingIcon = { RadioButton(selected = s == siralama, onClick = null) },
                                onClick = { vm.siralamaSec(s); siralamaMenusu = false }
                            )
                        }
                    }
                }
            }
        )
        OutlinedTextField(
            value = arama, onValueChange = { vm.arama.value = it },
            placeholder = { Text("Türkü ara…") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (arama.isNotEmpty()) IconButton(onClick = { vm.arama.value = "" }) { Icon(Icons.Filled.Clear, "Temizle") }
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun EkleButonu(sadeceDokuman: Boolean, ekleme: EklemeDurumu) {
    var acik by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = { acik = true }) { Icon(Icons.Filled.Add, "Ekle") }
        DropdownMenu(expanded = acik, onDismissRequest = { acik = false }) {
            fun sec(t: EkleTuru) { acik = false; ekleme.baslat(t) }
            if (!sadeceDokuman) {
                DropdownMenuItem(text = { Text("Yeni türkü") }, leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                    onClick = { sec(EkleTuru.YENI_TURKU) })
                DropdownMenuItem(text = { Text("Video ekle (telefondan)") }, leadingIcon = { Icon(Icons.Filled.VideoLibrary, null) },
                    onClick = { sec(EkleTuru.VIDEO_DOSYA) })
                DropdownMenuItem(text = { Text("Video linki yapıştır") }, leadingIcon = { Icon(Icons.Filled.Link, null) },
                    onClick = { sec(EkleTuru.VIDEO_LINK) })
                HorizontalDivider()
            }
            DropdownMenuItem(text = { Text("Doküman ekle (PDF / resim / Word)") }, leadingIcon = { Icon(Icons.Filled.NoteAdd, null) },
                onClick = { sec(EkleTuru.DOKUMAN) })
            DropdownMenuItem(text = { Text("Nota tara (kamera)") }, leadingIcon = { Icon(Icons.Filled.DocumentScanner, null) },
                onClick = { sec(EkleTuru.TARAMA) })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("WhatsApp'tan nasıl eklerim?") }, leadingIcon = { Icon(Icons.Filled.Info, null) },
                onClick = { sec(EkleTuru.WHATSAPP_BILGI) })
        }
    }
}

@Composable
private fun TurkuListesi(
    vm: ArsivViewModel,
    sadeceFavori: Boolean,
    turkuAc: (Long) -> Unit,
    videoAc: (Long) -> Unit,
    yedeklemeyeGit: () -> Unit
) {
    val liste by vm.turkuListesi.collectAsState()
    val izgara by vm.izgara.collectAsState()
    val siralama by vm.siralama.collectAsState()
    val duzenle by vm.siraDuzenle.collectAsState()
    val arama by vm.arama.collectAsState()
    val gosterilen = if (sadeceFavori) liste.filter { it.turku.favori } else liste
    val duzenleAktif = duzenle && siralama == Siralama.OZEL && arama.isBlank()
    val sonIzlenen by vm.sonIzlenen.collectAsState()
    val yedeksiz by vm.yedeksizSayisi.collectAsState()
    val sonYedek by vm.sonYedek.collectAsState()
    val ustKartlar = !sadeceFavori && arama.isBlank() && !duzenleAktif
    val yedekHatirlat = ustKartlar && yedeksiz > 0 &&
        System.currentTimeMillis() - sonYedek > 14L * 24 * 60 * 60 * 1000
    val devam = if (ustKartlar) sonIzlenen else null

    if (gosterilen.isEmpty()) {
        when {
            arama.isNotBlank() -> BosDurum(Icons.Filled.Search, "Sonuç yok", "\"$arama\" ile eşleşen türkü bulunamadı.")
            sadeceFavori -> BosDurum(Icons.Filled.StarBorder, "Favori türkü yok", "Bir türkünün yıldızına dokunarak favorilere ekleyebilirsin.")
            else -> BosDurum(
                Icons.Filled.LibraryMusic, "Arşivin henüz boş",
                "WhatsApp'ta bir ders videosunu aç, Paylaş → Bağlama Arşivim'i seç. Ya da sağ alttaki + ile ekle."
            )
        }
    } else if (izgara && !duzenleAktif) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (devam != null) item(span = { GridItemSpan(maxLineSpan) }, key = "devam") {
                DevamKarti(vm, devam, Modifier.padding(4.dp)) { videoAc(devam.video.id) }
            }
            if (yedekHatirlat) item(span = { GridItemSpan(maxLineSpan) }, key = "yedek") {
                YedekHatirlatici(yedeksiz, Modifier.padding(4.dp), yedeklemeyeGit)
            }
            items(gosterilen, key = { it.turku.id }) { o ->
                TurkuKarti(vm, o, Modifier.padding(4.dp)) { turkuAc(o.turku.id) }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (devam != null) item(key = "devam") {
                DevamKarti(vm, devam, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { videoAc(devam.video.id) }
            }
            if (yedekHatirlat) item(key = "yedek") {
                YedekHatirlatici(yedeksiz, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), yedeklemeyeGit)
            }
            itemsIndexed(gosterilen, key = { _, o -> o.turku.id }) { i, o ->
                ListItem(
                    modifier = Modifier.clickable { turkuAc(o.turku.id) },
                    leadingContent = {
                        VideoKucukResim(
                            o.ilkVideo?.let { vm.repo.videoDosya(it) },
                            Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                        )
                    },
                    headlineContent = { Text(o.turku.ad, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text("${o.videoSayisi} video · ${o.dokumanSayisi} doküman · ${tarihYaz(o.turku.eklenmeTarihi)}",
                            style = MaterialTheme.typography.bodySmall)
                    },
                    trailingContent = {
                        if (duzenleAktif) {
                            Row {
                                IconButton(enabled = i > 0, onClick = { vm.yerDegistir(o.turku, gosterilen[i - 1].turku) }) {
                                    Icon(Icons.Filled.KeyboardArrowUp, "Yukarı")
                                }
                                IconButton(enabled = i < gosterilen.lastIndex, onClick = { vm.yerDegistir(o.turku, gosterilen[i + 1].turku) }) {
                                    Icon(Icons.Filled.KeyboardArrowDown, "Aşağı")
                                }
                            }
                        } else {
                            FavoriButonu(o.turku.favori) { vm.favori(o.turku) }
                        }
                    }
                )
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
fun FavoriButonu(favori: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (favori) Icons.Filled.Star else Icons.Filled.StarBorder,
            if (favori) "Favorilerden çıkar" else "Favorilere ekle",
            tint = if (favori) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun TurkuKarti(vm: ArsivViewModel, o: TurkuOzet, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick)) {
        Box {
            VideoKucukResim(
                o.ilkVideo?.let { vm.repo.videoDosya(it) },
                Modifier.fillMaxWidth().aspectRatio(16f / 10f)
            )
            Box(Modifier.align(Alignment.TopEnd)) { FavoriButonu(o.turku.favori) { vm.favori(o.turku) } }
        }
        Column(Modifier.padding(10.dp)) {
            Text(o.turku.ad, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VideoLibrary, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" ${o.videoSayisi}   ", style = MaterialTheme.typography.labelSmall)
                Icon(Icons.Filled.Description, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" ${o.dokumanSayisi}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** "Kaldığın yerden devam" — son izlenen videoya tek dokunuşla dönüş. */
@Composable
private fun DevamKarti(vm: ArsivViewModel, s: SonIzlenen, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
                VideoKucukResim(vm.repo.videoDosya(s.video), Modifier.fillMaxSize())
                Icon(Icons.Filled.PlayArrow, null, Modifier.align(Alignment.Center).size(36.dp),
                    tint = MaterialTheme.colorScheme.surface)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Kaldığın yerden devam", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(s.turkuAdi, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${s.video.baslik} · ${sureYaz(s.video.sonKonumMs)}", style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun YedekHatirlatici(yedeksiz: Int, modifier: Modifier, yedekle: () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudUpload, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                "$yedeksiz dosya henüz yedeklenmedi. Telefon değişirse ya da uygulama silinirse kaybolur.",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = yedekle) { Text("Yedekle") }
        }
    }
}
