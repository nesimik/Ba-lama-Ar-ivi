package com.baglamaarsivim.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baglamaarsivim.util.TR
import java.text.Collator

/** WhatsApp vb.'den "Paylaş" ile gelen dosyaları bir türküye atama ekranı. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IceAktarEkrani(vm: ArsivViewModel, bitti: (turkuId: Long?) -> Unit) {
    val paket by vm.gelen.collectAsState()
    val turkuler by vm.turkuler.collectAsState()
    var secili by remember { mutableStateOf<Long?>(null) }
    var turkusuz by remember { mutableStateOf(false) }
    var yeniAd by remember { mutableStateOf("") }
    var ara by remember { mutableStateOf("") }
    val collator = remember { Collator.getInstance(TR) }

    var tamamlandi by remember { mutableStateOf(false) }
    val iptal = { tamamlandi = true; vm.paylasimIptal(); bitti(null) }
    BackHandler { iptal() }

    val p = paket
    // Kopyalama sonunda desteklenen dosya çıkmazsa paket null olur: ekranı kapat.
    LaunchedEffect(p == null) {
        if (p == null && !tamamlandi) { tamamlandi = true; bitti(null) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arşive ekle") },
                navigationIcon = { IconButton(onClick = iptal) { Icon(Icons.Filled.Close, "İptal") } }
            )
        },
        bottomBar = {
            if (p != null && p.hazir) {
                val videoVar = p.dosyalar.any { it.video }
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = iptal, modifier = Modifier.weight(1f)) { Text("İptal") }
                        Button(
                            enabled = secili != null || (turkusuz && !videoVar),
                            onClick = { tamamlandi = true; vm.paylasimiKaydet(if (turkusuz) null else secili) { bitti(it) } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Kaydet") }
                    }
                }
            }
        }
    ) { ic ->
        if (p == null) {
            Box(Modifier.padding(ic).fillMaxSize())
        } else if (!p.hazir) {
            Column(Modifier.padding(ic).fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Dosyalar telefona kopyalanıyor…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(progress = { p.ilerleme }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("%${(p.ilerleme * 100).toInt()}")
            }
        } else {
            val videoVar = p.dosyalar.any { it.video }
            val q = ara.trim().lowercase(TR)
            val liste = turkuler.filter { q.isEmpty() || it.ad.lowercase(TR).contains(q) }.sortedWith(compareBy(collator) { it.ad })
            LazyColumn(Modifier.padding(ic).fillMaxSize()) {
                item {
                    Text("Gelen dosyalar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
                }
                items(p.dosyalar) { g ->
                    ListItem(
                        leadingContent = { Icon(if (g.video) Icons.Filled.Videocam else dokumanIkonu(g.dokTipi!!), null) },
                        headlineContent = { Text(g.ad, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(if (g.video) "Video" else "Doküman (${g.dokTipi?.name})") }
                    )
                }
                if (p.desteklenmeyen > 0) {
                    item {
                        Text("${p.desteklenmeyen} dosya desteklenmediği için atlandı.", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Hangi türküye ait?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                    OutlinedTextField(
                        value = yeniAd, onValueChange = { yeniAd = it }, singleLine = true,
                        label = { Text("Yeni türkü oluştur") },
                        trailingIcon = {
                            IconButton(enabled = yeniAd.isNotBlank(), onClick = {
                                vm.turkuOlustur(yeniAd) { id -> secili = id; turkusuz = false }
                                yeniAd = ""
                            }) { Icon(Icons.Filled.Add, "Oluştur") }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    if (turkuler.size > 6) {
                        OutlinedTextField(
                            value = ara, onValueChange = { ara = it }, singleLine = true, label = { Text("Türkü ara") },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    }
                }
                if (!videoVar) {
                    item {
                        SecimSatiri("Türküye bağlama", "Sadece Notalar Arşivi'nde dursun", Icons.Filled.FolderOpen, turkusuz) {
                            turkusuz = true; secili = null
                        }
                    }
                }
                if (q.isEmpty() && turkuler.size > 5) {
                    val sonlar = turkuler.sortedByDescending { it.eklenmeTarihi }.take(3)
                    item {
                        Text("Son eklenen türküler", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                    }
                    items(sonlar, key = { "son${it.id}" }) { t ->
                        SecimSatiri(t.ad, null, Icons.Filled.MusicNote, !turkusuz && secili == t.id) {
                            secili = t.id; turkusuz = false
                        }
                    }
                    item {
                        Text("Tüm türküler", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                    }
                }
                items(liste, key = { it.id }) { t ->
                    SecimSatiri(t.ad, null, Icons.Filled.MusicNote, !turkusuz && secili == t.id) {
                        secili = t.id; turkusuz = false
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SecimSatiri(
    baslik: String, alt: String?, ikon: androidx.compose.ui.graphics.vector.ImageVector,
    secili: Boolean, onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(ikon, null, Modifier.size(24.dp)) },
        headlineContent = { Text(baslik) },
        supportingContent = if (alt != null) { { Text(alt) } } else null,
        trailingContent = { RadioButton(selected = secili, onClick = onClick) }
    )
}
