package com.baglamaarsivim.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import com.baglamaarsivim.data.Dokuman
import com.baglamaarsivim.data.DokumanOzet
import com.baglamaarsivim.data.DokumanTipi
import com.baglamaarsivim.util.TR
import com.baglamaarsivim.util.boyutYaz
import com.baglamaarsivim.util.tarihYaz

/** Türküye bağlı olsun olmasın tüm dokümanların tek listesi. */
@Composable
fun NotalarEkrani(vm: ArsivViewModel, ac: (DokumanOzet) -> Unit) {
    val dokumanlar by vm.dokumanlar.collectAsState()
    var ara by rememberSaveable { mutableStateOf("") }
    var filtre by rememberSaveable { mutableStateOf<String?>(null) }
    val q = ara.trim().lowercase(TR)
    val liste = dokumanlar.filter { o ->
        (q.isEmpty() || o.dokuman.dosyaAdi.lowercase(TR).contains(q) || (o.turkuAdi?.lowercase(TR)?.contains(q) == true)) &&
            (filtre == null || o.dokuman.tip.name == filtre)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ara, onValueChange = { ara = it }, singleLine = true,
            placeholder = { Text("Doküman veya türkü adı ara…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (ara.isNotEmpty()) IconButton(onClick = { ara = "" }) { Icon(Icons.Filled.Clear, "Temizle") } },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filtre == null, onClick = { filtre = null }, label = { Text("Tümü") })
            FilterChip(selected = filtre == "PDF", onClick = { filtre = "PDF" }, label = { Text("PDF") })
            FilterChip(selected = filtre == "IMAGE", onClick = { filtre = "IMAGE" }, label = { Text("Resim") })
            FilterChip(selected = filtre == "WORD", onClick = { filtre = "WORD" }, label = { Text("Word") })
        }
        if (liste.isEmpty()) {
            BosDurum(
                Icons.Filled.Description,
                if (dokumanlar.isEmpty()) "Henüz nota yok" else "Sonuç yok",
                if (dokumanlar.isEmpty()) "PDF, fotoğraf veya Word notalarını + ile ekleyebilir, kamerayla tarayabilir ya da WhatsApp'tan paylaşabilirsin." else "Aramana uyan doküman bulunamadı."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(liste, key = { it.dokuman.id }) { o ->
                    DokumanSatiri(vm, o.dokuman, o.turkuAdi ?: "Türküye bağlı değil") { ac(o) }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

/** Doküman satırı: önizleme, bilgi, yedek durumu ve (yeniden adlandır / türkü değiştir / sil) menüsü. */
@Composable
fun DokumanSatiri(vm: ArsivViewModel, d: Dokuman, altBaslik: String?, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var adlandir by remember { mutableStateOf(false) }
    var turkuDegistir by remember { mutableStateOf(false) }
    var sil by remember { mutableStateOf(false) }
    val turkuler by vm.turkuler.collectAsState()

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            if (d.tip == DokumanTipi.IMAGE) {
                ResimKucuk(vm.repo.dokDosya(d), Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
            } else {
                Box(Modifier.size(48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(dokumanIkonu(d.tip), null, Modifier.size(32.dp),
                        tint = if (d.tip == DokumanTipi.PDF) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        },
        headlineContent = { Text(d.dosyaAdi, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                if (altBaslik != null) Text("♪ $altBaslik", style = MaterialTheme.typography.bodySmall)
                Text("${d.tip.name} · ${boyutYaz(d.boyutByte)} · ${tarihYaz(d.eklenmeTarihi)}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(2.dp))
                YedekDurumu(d.yedekTarihi)
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Seçenekler") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Yeniden adlandır") }, leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                        onClick = { menu = false; adlandir = true })
                    DropdownMenuItem(text = { Text("Türküsünü değiştir") }, leadingIcon = { Icon(Icons.Filled.MusicNote, null) },
                        onClick = { menu = false; turkuDegistir = true })
                    DropdownMenuItem(text = { Text("Sil") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; sil = true })
                }
            }
        }
    )

    if (adlandir) MetinDialog(
        baslik = "Dokümanı yeniden adlandır", ilkDeger = d.dosyaAdi,
        onKapat = { adlandir = false }, onOnay = { vm.dokumanGuncelle(d.copy(dosyaAdi = it)); adlandir = false }
    )
    if (turkuDegistir) TurkuSecDialog(
        turkuler = turkuler, turkusuzIzin = true, baslik = "Hangi türküye ait?",
        onKapat = { turkuDegistir = false },
        onSec = { id -> vm.dokumanGuncelle(d.copy(turkuId = id)); turkuDegistir = false },
        onYeni = { ad -> vm.turkuOlustur(ad) { id -> vm.dokumanGuncelle(d.copy(turkuId = id)) }; turkuDegistir = false }
    )
    if (sil) OnayDialog(
        baslik = "Doküman silinsin mi?",
        metin = "\"${d.dosyaAdi}\" ve üzerindeki tüm çizim/notlar kalıcı olarak silinecek.",
        onKapat = { sil = false }, onOnay = { vm.dokumanSil(d); sil = false }
    )
}
