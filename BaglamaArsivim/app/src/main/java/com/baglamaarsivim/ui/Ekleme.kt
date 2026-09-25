package com.baglamaarsivim.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baglamaarsivim.util.dosyaUri
import java.io.File

enum class EkleTuru { VIDEO_DOSYA, VIDEO_LINK, DOKUMAN, TARAMA, YENI_TURKU, WHATSAPP_BILGI }

val DOKUMAN_MIME = arrayOf(
    "application/pdf", "image/*", "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
)

/** "+" menüsünden ya da türkü sayfasından başlatılan ekleme akışının durumu. */
@Stable
class EklemeDurumu {
    var tur by mutableStateOf<EkleTuru?>(null)
    var turkuSecimi by mutableStateOf(false)
    var hedefTurku by mutableStateOf<Long?>(null)
    var linkPenceresi by mutableStateOf(false)
    var tetik by mutableIntStateOf(0)

    /** [turkuId] verilirse türkü sorulmadan doğrudan o türküye eklenir. */
    fun baslat(t: EkleTuru, turkuId: Long? = null) {
        tur = t
        hedefTurku = turkuId
        when {
            t == EkleTuru.YENI_TURKU || t == EkleTuru.WHATSAPP_BILGI -> Unit
            turkuId != null -> tetik++
            else -> turkuSecimi = true
        }
    }

    fun bitir() {
        tur = null; turkuSecimi = false; linkPenceresi = false
    }
}

@Composable
fun rememberEklemeDurumu() = remember { EklemeDurumu() }

@Composable
fun EklemeAkisi(vm: ArsivViewModel, durum: EklemeDurumu, turkuAc: (Long) -> Unit = {}) {
    val ctx = LocalContext.current
    val turkuler by vm.turkuler.collectAsState()
    var taramaYolu by rememberSaveable { mutableStateOf<String?>(null) }
    var taramaTurku by rememberSaveable { mutableLongStateOf(-1L) }

    val videoSecici = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uriler ->
        durum.hedefTurku?.let { vm.videolariEkle(it, uriler) }
        durum.bitir()
    }
    val dokumanSecici = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uriler ->
        vm.dokumanlariEkle(durum.hedefTurku, uriler)
        durum.bitir()
    }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { tamam ->
        val yol = taramaYolu
        if (tamam && yol != null) vm.taramaEkle(taramaTurku.takeIf { it >= 0 }, File(yol))
        taramaYolu = null
        durum.bitir()
    }
    val bildirimIzni = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(durum.tetik) {
        if (durum.tetik == 0) return@LaunchedEffect
        try {
            when (durum.tur) {
                EkleTuru.VIDEO_DOSYA -> videoSecici.launch(arrayOf("video/*"))
                EkleTuru.DOKUMAN -> dokumanSecici.launch(DOKUMAN_MIME)
                EkleTuru.VIDEO_LINK -> durum.linkPenceresi = true
                EkleTuru.TARAMA -> {
                    val f = File(ctx.cacheDir, "tarama_${System.currentTimeMillis()}.jpg")
                    taramaYolu = f.absolutePath
                    taramaTurku = durum.hedefTurku ?: -1L
                    kamera.launch(dosyaUri(ctx, f))
                }
                else -> Unit
            }
        } catch (e: Exception) {
            vm.mesaj("Bu işlem için uygun bir uygulama bulunamadı")
            durum.bitir()
        }
    }

    if (durum.turkuSecimi) {
        val dokumanMi = durum.tur == EkleTuru.DOKUMAN || durum.tur == EkleTuru.TARAMA
        TurkuSecDialog(
            turkuler = turkuler,
            turkusuzIzin = dokumanMi,
            onKapat = { durum.bitir() },
            onSec = { id ->
                durum.hedefTurku = id; durum.turkuSecimi = false; durum.tetik++
            },
            onYeni = { ad ->
                vm.turkuOlustur(ad) { id ->
                    durum.hedefTurku = id; durum.turkuSecimi = false; durum.tetik++
                }
            }
        )
    }

    if (durum.linkPenceresi) {
        LinkDialog(
            onKapat = { durum.bitir() },
            onIndir = { url ->
                val t = durum.hedefTurku
                if (t != null && vm.linkIndir(url, t)) {
                    if (Build.VERSION.SDK_INT >= 33) bildirimIzni.launch(Manifest.permission.POST_NOTIFICATIONS)
                    durum.bitir()
                }
            }
        )
    }

    when (durum.tur) {
        EkleTuru.YENI_TURKU -> MetinDialog(
            baslik = "Yeni türkü", etiket = "Türkü adı", onayMetni = "Oluştur",
            onKapat = { durum.bitir() },
            onOnay = { ad -> vm.turkuOlustur(ad) { id -> turkuAc(id) }; durum.bitir() }
        )
        EkleTuru.WHATSAPP_BILGI -> WhatsAppBilgiDialog { durum.bitir() }
        else -> Unit
    }
}

@Composable
fun LinkDialog(onKapat: () -> Unit, onIndir: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    val pano = LocalClipboardManager.current
    LaunchedEffect(Unit) {
        pano.getText()?.text?.trim()?.takeIf { it.startsWith("http") }?.let { url = it }
    }
    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text("Link'ten video indir") },
        text = {
            Column {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, label = { Text("Video linki") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                BilgiKarti(
                    "WhatsApp linkleri kısa ömürlüdür ve genelde dışarıdan indirilemez. WhatsApp videoları için: " +
                        "videoyu aç → Paylaş → Bağlama Arşivim. YouTube ve benzeri platformlardan indirme desteklenmez."
                )
            }
        },
        confirmButton = { TextButton(enabled = url.isNotBlank(), onClick = { onIndir(url) }) { Text("İndir") } },
        dismissButton = { TextButton(onClick = onKapat) { Text("İptal") } }
    )
}

@Composable
fun BilgiKarti(metin: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.size(8.dp))
            Text(metin, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun WhatsAppBilgiDialog(onKapat: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text("WhatsApp'tan nasıl eklerim?") },
        text = {
            Column {
                Text("1. WhatsApp grubunda videoyu (veya nota PDF'ini/fotoğrafını) aç.")
                Text("2. Sağ üstteki ⋮ menüsünden ya da uzun basarak \"Paylaş\"ı seç.")
                Text("3. Listeden \"Bağlama Arşivim\"i seç.")
                Text("4. Hangi türküye ait olduğunu seç ya da yeni türkü oluştur.")
                Spacer(Modifier.height(12.dp))
                BilgiKarti("Birden fazla videoyu seçip tek seferde paylaşabilirsin. Dosya hemen telefonuna kopyalanır; WhatsApp'tan silinse bile arşivde kalır.")
            }
        },
        confirmButton = { TextButton(onClick = onKapat) { Text("Anladım") } }
    )
}
