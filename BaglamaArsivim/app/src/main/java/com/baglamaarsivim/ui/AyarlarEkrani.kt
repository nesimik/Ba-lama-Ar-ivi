package com.baglamaarsivim.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.baglamaarsivim.util.boyutYaz
import com.baglamaarsivim.util.tarihSaatYaz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AyarlarEkrani(vm: ArsivViewModel) {
    val yedeksiz by vm.yedeksizSayisi.collectAsState()
    val sonYedek by vm.sonYedek.collectAsState()
    val islem by vm.islem.collectAsState()
    var geriYukleSorusu by remember { mutableStateOf(false) }
    var degistirModu by remember { mutableStateOf(false) }

    val kullanim by produceState(0L to 0L, islem) {
        value = withContext(Dispatchers.IO) { vm.repo.videoKullanimi() to vm.repo.dokumanKullanimi() }
    }

    val yedekKaydet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.yedekle(it, null) }
    }
    val yedekAc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.geriYukle(it, degistirModu) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AyarKarti(Icons.Filled.Backup, "Yedekleme (manuel)") {
            Text(
                "Tüm arşiv — videolar, notalar, türkü sıraları, favoriler, hoca notları ve çizimler — tek bir .zip " +
                    "dosyasına yedeklenir. Kaydetme ekranında sol menüden \"Drive\"ı seçersen yedek doğrudan Google Drive'a gider. " +
                    "Otomatik yedekleme yoktur; yedek sadece sen istediğinde alınır.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (sonYedek > 0) "Son tam yedek: ${tarihSaatYaz(sonYedek)}" else "Henüz tam yedek alınmadı",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                if (yedeksiz > 0) "Yedeklenmemiş dosya: $yedeksiz" else "Tüm dosyalar yedeklendi ✅",
                style = MaterialTheme.typography.labelSmall,
                color = if (yedeksiz > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val tarih = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT).format(Date())
                yedekKaydet.launch("BaglamaArsivim_Yedek_$tarih.zip")
            }, modifier = Modifier.fillMaxWidth()) { Text("Tümünü Yedekle") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { geriYukleSorusu = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Yedekten Geri Yükle")
            }
        }

        AyarKarti(Icons.Filled.Storage, "Depolama") {
            Text("Videolar: ${boyutYaz(kullanim.first)}", style = MaterialTheme.typography.bodyMedium)
            Text("Dokümanlar: ${boyutYaz(kullanim.second)}", style = MaterialTheme.typography.bodyMedium)
            Text("Toplam: ${boyutYaz(kullanim.first + kullanim.second)}", style = MaterialTheme.typography.titleMedium)
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.size(12.dp))
                Text(
                    "Uygulama silinirse ya da verileri temizlenirse telefondaki arşiv de silinir. " +
                        "Önemli videoları kaybetmemek için düzenli olarak yedek al.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        AyarKarti(Icons.Filled.Info, "Hakkında") {
            Text("Bağlama Arşivim 1.0", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Saz derslerinin videolarını ve notalarını çevrimdışı, düzenli ve kalıcı tutmak için. " +
                    "Tüm veriler yalnızca bu telefonda saklanır.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (geriYukleSorusu) {
        AlertDialog(
            onDismissRequest = { geriYukleSorusu = false },
            title = { Text("Nasıl geri yüklensin?") },
            text = {
                Text(
                    "Birleştir: Mevcut arşiv korunur, yedekteki eksikler eklenir (aynı adlı türküler eşleşir).\n\n" +
                        "Tümünü değiştir: Telefondaki arşiv tamamen silinir ve yedekle değiştirilir."
                )
            },
            confirmButton = {
                TextButton(onClick = { degistirModu = false; geriYukleSorusu = false; yedekAc.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                    Text("Birleştir")
                }
            },
            dismissButton = {
                TextButton(onClick = { degistirModu = true; geriYukleSorusu = false; yedekAc.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                    Text("Tümünü değiştir", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@Composable
private fun AyarKarti(ikon: ImageVector, baslik: String, icerik: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ikon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(baslik, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            icerik()
        }
    }
}
