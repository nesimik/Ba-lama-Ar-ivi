package com.baglamaarsivim.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.baglamaarsivim.data.DokumanTipi
import com.baglamaarsivim.data.Turku
import com.baglamaarsivim.util.TR
import com.baglamaarsivim.util.resimYukle
import com.baglamaarsivim.util.tarihYaz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator

private val kucukResimOnbellegi = LruCache<String, ImageBitmap>(120)

/** Videonun ilk saniyesinden küçük resim üretir (önbellekli). */
@Composable
fun VideoKucukResim(dosya: File?, modifier: Modifier = Modifier) {
    val resim by produceState<ImageBitmap?>(dosya?.let { kucukResimOnbellegi.get(it.path) }, dosya?.path) {
        if (dosya == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(dosya.absolutePath)
                    val kare: Bitmap? = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: r.frameAtTime
                    kare?.let { k ->
                        val w = 360
                        val h = (w.toFloat() * k.height / k.width).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(k, w, h, true).asImageBitmap()
                    }
                } finally {
                    r.release()
                }
            }.getOrNull()?.also { kucukResimOnbellegi.put(dosya.path, it) }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val r = resim
        if (r != null) Image(r, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** Resim dokümanları için küçük önizleme. */
@Composable
fun ResimKucuk(dosya: File, modifier: Modifier = Modifier) {
    val resim by produceState<ImageBitmap?>(kucukResimOnbellegi.get(dosya.path), dosya.path) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { resimYukle(dosya, 240)?.asImageBitmap() }.getOrNull()
                ?.also { kucukResimOnbellegi.put(dosya.path, it) }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val r = resim
        if (r != null) Image(r, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.outline)
    }
}

fun dokumanIkonu(tip: DokumanTipi): ImageVector = when (tip) {
    DokumanTipi.PDF -> Icons.Filled.PictureAsPdf
    DokumanTipi.IMAGE -> Icons.Filled.Description
    DokumanTipi.WORD -> Icons.Filled.Description
}

@Composable
fun YedekDurumu(tarih: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (tarih != null) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.size(4.dp))
            Text("Yedeklendi ✅ ${tarihYaz(tarih)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        } else {
            Icon(Icons.Filled.CloudOff, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.size(4.dp))
            Text("Yedeklenmedi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun BosDurum(ikon: ImageVector, baslik: String, aciklama: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(ikon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp))
        Text(baslik, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(aciklama, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MetinDialog(
    baslik: String,
    ilkDeger: String = "",
    etiket: String = "Ad",
    onayMetni: String = "Kaydet",
    cokSatirli: Boolean = false,
    onKapat: () -> Unit,
    onOnay: (String) -> Unit
) {
    var metin by remember { mutableStateOf(ilkDeger) }
    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text(baslik) },
        text = {
            OutlinedTextField(
                value = metin, onValueChange = { metin = it }, label = { Text(etiket) },
                singleLine = !cokSatirli, minLines = if (cokSatirli) 3 else 1,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = metin.isNotBlank(), onClick = { onOnay(metin.trim()) }) { Text(onayMetni) }
        },
        dismissButton = { TextButton(onClick = onKapat) { Text("İptal") } }
    )
}

@Composable
fun OnayDialog(
    baslik: String,
    metin: String,
    onayMetni: String = "Sil",
    secenekMetni: String? = null,
    onKapat: () -> Unit,
    onOnay: (secenek: Boolean) -> Unit
) {
    var secenek by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text(baslik) },
        text = {
            Column {
                Text(metin)
                if (secenekMetni != null) {
                    Row(
                        Modifier.fillMaxWidth().clickable { secenek = !secenek }.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = secenek, onCheckedChange = { secenek = it })
                        Text(secenekMetni, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOnay(secenek) }) {
                Text(onayMetni, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onKapat) { Text("Vazgeç") } }
    )
}

/**
 * Türkü seçme penceresi: mevcut bir türküyü seç ya da yeni türkü oluştur.
 * [turkusuzIzin] true ise dokümanı hiçbir türküye bağlamadan kaydetme seçeneği de sunulur.
 */
@Composable
fun TurkuSecDialog(
    turkuler: List<Turku>,
    turkusuzIzin: Boolean,
    baslik: String = "Hangi türküye eklensin?",
    onKapat: () -> Unit,
    onSec: (Long?) -> Unit,
    onYeni: (String) -> Unit
) {
    var ara by remember { mutableStateOf("") }
    var yeniAd by remember { mutableStateOf("") }
    val collator = remember { Collator.getInstance(TR) }
    val liste = remember(turkuler, ara) {
        val q = ara.trim().lowercase(TR)
        turkuler.filter { q.isEmpty() || it.ad.lowercase(TR).contains(q) }.sortedWith(compareBy(collator) { it.ad })
    }
    AlertDialog(
        onDismissRequest = onKapat,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = { Text(baslik) },
        text = {
            Column {
                OutlinedTextField(
                    value = yeniAd, onValueChange = { yeniAd = it },
                    label = { Text("Yeni türkü adı") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(enabled = yeniAd.isNotBlank(), onClick = { onYeni(yeniAd.trim()) }) {
                            Icon(Icons.Filled.Add, "Oluştur")
                        }
                    }
                )
                if (turkuler.size > 6) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ara, onValueChange = { ara = it }, label = { Text("Mevcut türkülerde ara") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (turkusuzIzin) {
                        item {
                            ListItem(
                                headlineContent = { Text("Türküye bağlama") },
                                supportingContent = { Text("Sadece Notalar Arşivi'nde görünsün") },
                                leadingContent = { Icon(Icons.Filled.FolderOpen, null) },
                                modifier = Modifier.clickable { onSec(null) }
                            )
                            HorizontalDivider()
                        }
                    }
                    items(liste, key = { it.id }) { t ->
                        ListItem(
                            headlineContent = { Text(t.ad) },
                            leadingContent = { Icon(Icons.Filled.MusicNote, null) },
                            modifier = Modifier.clickable { onSec(t.id) }
                        )
                    }
                    if (liste.isEmpty() && !turkusuzIzin) {
                        item {
                            Text("Henüz türkü yok — yukarıya bir ad yazıp + ile oluştur.",
                                Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onKapat) { Text("İptal") } }
    )
}

/** Uzun süren işlemler (kopyalama, yedekleme, geri yükleme) için kapatılamayan ilerleme penceresi. */
@Composable
fun IslemDialog(islem: Islem) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(islem.baslik) },
        text = {
            Column {
                val p = islem.ilerleme
                if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("%${(p * 100).toInt()}", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                Text("Lütfen uygulamayı kapatma.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {}
    )
}
