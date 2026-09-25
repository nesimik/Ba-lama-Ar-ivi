package com.baglamaarsivim.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.baglamaarsivim.data.Dokuman
import com.baglamaarsivim.data.Turku
import com.baglamaarsivim.data.Video
import com.baglamaarsivim.util.boyutYaz
import com.baglamaarsivim.util.sureYaz
import com.baglamaarsivim.util.tarihYaz
import com.baglamaarsivim.work.IndirmeWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurkuDetayEkrani(
    vm: ArsivViewModel,
    turkuId: Long,
    geri: () -> Unit,
    videoAc: (Long) -> Unit,
    dokumanAc: (Dokuman) -> Unit
) {
    val dao = vm.repo.dao
    val turku by remember(turkuId) { dao.turkuFlow(turkuId) }.collectAsState(initial = null)
    val videolar by remember(turkuId) { dao.videolarFlow(turkuId) }.collectAsState(initial = emptyList())
    val dokumanlar by remember(turkuId) { dao.dokumanlarFlow(turkuId) }.collectAsState(initial = emptyList())
    val tumIndirmeler by vm.indirmeler.collectAsState()
    val gizlenen by vm.gizlenenIndirmeler.collectAsState()
    val indirmeler = tumIndirmeler.filter {
        IndirmeWorker.turkuEtiketi(turkuId) in it.tags && it.id !in gizlenen &&
            it.state != WorkInfo.State.SUCCEEDED && it.state != WorkInfo.State.CANCELLED
    }
    val ekleme = rememberEklemeDurumu()
    var menu by remember { mutableStateOf(false) }
    var adlandir by remember { mutableStateOf(false) }
    var sil by remember { mutableStateOf(false) }

    val yedekKaydet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.yedekle(it, turkuId) }
    }

    val t = turku
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t?.ad ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = geri) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    if (t != null) {
                        FavoriButonu(t.favori) { vm.favori(t) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Seçenekler") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Yeniden adlandır") }, leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                                    onClick = { menu = false; adlandir = true })
                                DropdownMenuItem(text = { Text("Bu türküyü yedekle") }, leadingIcon = { Icon(Icons.Filled.Backup, null) },
                                    onClick = {
                                        menu = false
                                        val tarih = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                                        yedekKaydet.launch("${t.ad}_yedek_$tarih.zip")
                                    })
                                DropdownMenuItem(text = { Text("Türküyü sil") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = { menu = false; sil = true })
                            }
                        }
                    }
                }
            )
        }
    ) { ic ->
        if (t == null) {
            Box(Modifier.padding(ic).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.padding(ic).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    BolumBasligi("Videolar (${videolar.size})")
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { ekleme.baslat(EkleTuru.VIDEO_DOSYA, turkuId) }, label = { Text("Telefondan") },
                            leadingIcon = { Icon(Icons.Filled.VideoLibrary, null, Modifier.size(18.dp)) })
                        AssistChip(onClick = { ekleme.baslat(EkleTuru.VIDEO_LINK, turkuId) }, label = { Text("Link") },
                            leadingIcon = { Icon(Icons.Filled.Link, null, Modifier.size(18.dp)) })
                    }
                }
                items(indirmeler, key = { it.id.toString() }) { w -> IndirmeSatiri(vm, w) }
                if (videolar.isEmpty() && indirmeler.isEmpty()) {
                    item {
                        Text("Henüz video yok. WhatsApp'ta videoyu Paylaş → Bağlama Arşivim ile de ekleyebilirsin.",
                            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(videolar, key = { "v${it.id}" }) { v -> VideoSatiri(vm, v) { videoAc(v.id) } }

                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    BolumBasligi("Notalar ve dokümanlar (${dokumanlar.size})")
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { ekleme.baslat(EkleTuru.DOKUMAN, turkuId) }, label = { Text("Dosya seç") },
                            leadingIcon = { Icon(Icons.Filled.NoteAdd, null, Modifier.size(18.dp)) })
                        AssistChip(onClick = { ekleme.baslat(EkleTuru.TARAMA, turkuId) }, label = { Text("Tara") },
                            leadingIcon = { Icon(Icons.Filled.DocumentScanner, null, Modifier.size(18.dp)) })
                    }
                }
                items(dokumanlar, key = { "d${it.id}" }) { d -> DokumanSatiri(vm, d, null) { dokumanAc(d) } }

                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    BolumBasligi("Hocanın notları")
                    NotAlani(vm, t)
                }
            }
        }
    }

    EklemeAkisi(vm, ekleme)

    if (adlandir && t != null) MetinDialog(
        baslik = "Türküyü yeniden adlandır", ilkDeger = t.ad, etiket = "Türkü adı",
        onKapat = { adlandir = false }, onOnay = { vm.turkuGuncelle(t.copy(ad = it)); adlandir = false }
    )
    if (sil && t != null) OnayDialog(
        baslik = "\"${t.ad}\" silinsin mi?",
        metin = "Bu türküye ait ${videolar.size} video telefondan kalıcı olarak silinecek. Bu işlem geri alınamaz.",
        secenekMetni = "Dokümanları da sil (işaretlemezsen Notalar Arşivi'nde kalırlar)",
        onKapat = { sil = false },
        onOnay = { dokDa -> sil = false; vm.turkuSil(t, dokDa); geri() }
    )
}

@Composable
private fun BolumBasligi(metin: String) {
    Text(metin, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun NotAlani(vm: ArsivViewModel, t: Turku) {
    var not by remember(t.id) { mutableStateOf(t.notMetni ?: "") }
    val degisti = not != (t.notMetni ?: "")
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = not, onValueChange = { not = it },
            placeholder = { Text("Hocanın ipuçları, düzen, akort, tavır notları…") },
            minLines = 4, modifier = Modifier.fillMaxWidth()
        )
        if (degisti) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.turkuGuncelle(t.copy(notMetni = not.ifBlank { null })) }, modifier = Modifier.align(Alignment.End)) {
                Text("Notu kaydet")
            }
        }
    }
}

@Composable
private fun VideoSatiri(vm: ArsivViewModel, v: Video, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var adlandir by remember { mutableStateOf(false) }
    var sil by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            VideoKucukResim(vm.repo.videoDosya(v), Modifier.fillMaxSize())
            Icon(Icons.Filled.PlayCircle, null, Modifier.align(Alignment.Center).size(32.dp),
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.baslik, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${boyutYaz(v.boyutByte)} · ${tarihYaz(v.eklenmeTarihi)}", style = MaterialTheme.typography.bodySmall)
            if (v.sonKonumMs > 1000) {
                Text("▶ Kaldığın yer: ${sureYaz(v.sonKonumMs)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            YedekDurumu(v.yedekTarihi)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Seçenekler") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Yeniden adlandır") }, onClick = { menu = false; adlandir = true })
                DropdownMenuItem(text = { Text("Sil") }, onClick = { menu = false; sil = true })
            }
        }
    }
    if (adlandir) MetinDialog(
        baslik = "Video adı", ilkDeger = v.baslik,
        onKapat = { adlandir = false }, onOnay = { vm.videoGuncelle(v.copy(baslik = it)); adlandir = false }
    )
    if (sil) OnayDialog(
        baslik = "Video silinsin mi?", metin = "\"${v.baslik}\" telefondan kalıcı olarak silinecek.",
        onKapat = { sil = false }, onOnay = { vm.videoSil(v); sil = false }
    )
}

@Composable
private fun IndirmeSatiri(vm: ArsivViewModel, w: WorkInfo) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        when (w.state) {
            WorkInfo.State.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    w.outputData.getString(IndirmeWorker.ANAHTAR_HATA) ?: "İndirme başarısız",
                    Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                )
                IconButton(onClick = { vm.indirmeyiGizle(w.id) }) { Icon(Icons.Filled.Close, "Kapat") }
            }
            else -> {
                val oran = w.progress.getFloat(IndirmeWorker.ANAHTAR_ILERLEME, -1f)
                val inen = w.progress.getLong(IndirmeWorker.ANAHTAR_INEN, 0L)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (w.state == WorkInfo.State.ENQUEUED || w.state == WorkInfo.State.BLOCKED) "İnternet bekleniyor…"
                        else "İndiriliyor… ${boyutYaz(inen)}" + if (oran >= 0) " (%${(oran * 100).toInt()})" else "",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                    )
                    IconButton(onClick = { vm.indirmeyiIptal(w.id) }) { Icon(Icons.Filled.Close, "İptal") }
                }
                if (oran >= 0) LinearProgressIndicator(progress = { oran }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
