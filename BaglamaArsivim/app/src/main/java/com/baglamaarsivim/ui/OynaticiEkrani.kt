package com.baglamaarsivim.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.baglamaarsivim.data.Dokuman
import com.baglamaarsivim.data.Video
import com.baglamaarsivim.util.sureYaz
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val HIZLAR = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)

fun Context.aktivite(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun OynaticiEkrani(vm: ArsivViewModel, videoId: Long, geri: () -> Unit, dokumanAc: (Dokuman) -> Unit) {
    val video by produceState<Video?>(null, videoId) { value = vm.repo.dao.video(videoId) }
    val v = video
    if (v == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        OynaticiIcerik(vm, v, geri, dokumanAc)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OynaticiIcerik(vm: ArsivViewModel, v: Video, geri: () -> Unit, dokumanAc: (Dokuman) -> Unit) {
    val ctx = LocalContext.current
    val aktivite = remember { ctx.aktivite() }
    val dokumanlar by remember(v.turkuId) { vm.repo.dao.dokumanlarFlow(v.turkuId) }.collectAsState(initial = emptyList())

    val oynatici = remember(v.id) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(vm.repo.videoDosya(v))))
            prepare()
            if (v.sonKonumMs > 0) seekTo(v.sonKonumMs) // kaldığı yerden devam
            playWhenReady = true
        }
    }
    DisposableEffect(oynatici) {
        onDispose {
            val konum = if (oynatici.playbackState == Player.STATE_ENDED) 0L else oynatici.currentPosition
            vm.konumKaydet(v.id, konum)
            oynatici.release()
        }
    }
    // Uygulama arka plana geçince duraklat
    val yasamDongusu = LocalLifecycleOwner.current
    DisposableEffect(yasamDongusu) {
        val gozlemci = LifecycleEventObserver { _, olay ->
            if (olay == Lifecycle.Event.ON_STOP) {
                oynatici.pause()
                vm.konumKaydet(v.id, oynatici.currentPosition)
            }
        }
        yasamDongusu.lifecycle.addObserver(gozlemci)
        onDispose { yasamDongusu.lifecycle.removeObserver(gozlemci) }
    }

    var hiz by rememberSaveable { mutableFloatStateOf(vm.sonHiz) }
    LaunchedEffect(hiz) {
        oynatici.setPlaybackSpeed(hiz) // perde (ton) korunur, sadece tempo değişir
        vm.sonHiz = hiz
    }

    // A-B döngüsü: zor pasajı tekrar tekrar çalmak için
    var a by rememberSaveable { mutableStateOf<Long?>(null) }
    var b by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(a, b) {
        val bas = a
        val son = b
        if (bas != null && son != null && son > bas) {
            if (oynatici.currentPosition !in bas..son) oynatici.seekTo(bas)
            while (isActive) {
                if (oynatici.currentPosition >= son) oynatici.seekTo(bas)
                delay(40)
            }
        }
    }
    val aIsaretle: () -> Unit = {
        val p = oynatici.currentPosition
        a = p
        val mevcutB = b
        if (mevcutB != null && mevcutB <= p) b = null
    }
    val bIsaretle: () -> Unit = {
        val p = oynatici.currentPosition
        val bas = a
        if (bas != null && p > bas + 500) b = p
        else vm.mesaj("Önce A noktasını işaretle; B, A'dan sonra olmalı")
    }
    val atla: (Long) -> Unit = { ms ->
        val hedef = (oynatici.currentPosition + ms).coerceAtLeast(0L)
        oynatici.seekTo(if (oynatici.duration > 0) hedef.coerceAtMost(oynatici.duration) else hedef)
    }

    var tamEkran by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tamEkran) {
        val akt = aktivite ?: return@LaunchedEffect
        val denetleyici = WindowCompat.getInsetsController(akt.window, akt.window.decorView)
        if (tamEkran) {
            akt.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            denetleyici.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            denetleyici.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            akt.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            denetleyici.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            aktivite?.let { akt ->
                akt.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(akt.window, akt.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Video ile aynı ekranda açılan nota (bölünmüş görünüm)
    var acikNotaId by rememberSaveable { mutableStateOf<Long?>(null) }
    val acikNota = dokumanlar.firstOrNull { it.id == acikNotaId && it.tip != com.baglamaarsivim.data.DokumanTipi.WORD }

    BackHandler(enabled = tamEkran || acikNota != null) {
        if (tamEkran) tamEkran = false else acikNotaId = null
    }

    if (tamEkran) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            OynaticiGorunumu(oynatici, Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { hiz = sonrakiHiz(hiz) }) { Text("${hizYaz(hiz)}x", color = Color.White) }
                if (a != null && b != null) Icon(Icons.Filled.Repeat, "Döngü açık", tint = Color.White)
                IconButton(onClick = { tamEkran = false }) { Icon(Icons.Filled.FullscreenExit, "Tam ekrandan çık", tint = Color.White) }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(v.baslik, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = geri) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = { IconButton(onClick = { tamEkran = true }) { Icon(Icons.Filled.Fullscreen, "Tam ekran") } }
            )
        }
    ) { ic ->
        if (acikNota != null) {
            // ---- Bölünmüş görünüm: üstte video + kısa kontroller, altta nota ----
            Column(Modifier.padding(ic).fillMaxSize()) {
                OynaticiGorunumu(oynatici, Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { atla(-5000) }) { Icon(Icons.Filled.Replay5, "5 sn geri") }
                    IconButton(onClick = { atla(5000) }) { Icon(Icons.Filled.Forward5, "5 sn ileri") }
                    TextButton(onClick = { hiz = sonrakiHiz(hiz) }) { Text("Hız ${hizYaz(hiz)}x") }
                    TextButton(onClick = aIsaretle) { Text("A ${a?.let { sureYaz(it) } ?: "–"}") }
                    TextButton(onClick = bIsaretle) { Text("B ${b?.let { sureYaz(it) } ?: "–"}") }
                    if (a != null || b != null) IconButton(onClick = { a = null; b = null }) { Icon(Icons.Filled.Clear, "Döngüyü kapat") }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(acikNota.dosyaAdi, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { oynatici.pause(); dokumanAc(acikNota) }) { Text("Not al") }
                    IconButton(onClick = { acikNotaId = null }) { Icon(Icons.Filled.Close, "Notayı kapat") }
                }
                NotaOnizleme(vm, acikNota, Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Column(Modifier.padding(ic).fillMaxSize().verticalScroll(rememberScrollState())) {
                OynaticiGorunumu(oynatici, Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = { atla(-5000) }) {
                        Icon(Icons.Filled.Replay5, null, Modifier.size(20.dp)); Spacer(Modifier.size(6.dp)); Text("5 sn geri")
                    }
                    Spacer(Modifier.size(12.dp))
                    OutlinedButton(onClick = { atla(5000) }) {
                        Text("5 sn ileri"); Spacer(Modifier.size(6.dp)); Icon(Icons.Filled.Forward5, null, Modifier.size(20.dp))
                    }
                }

                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(8.dp))
                        Text("Çalma hızı", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HIZLAR.forEach { h ->
                            FilterChip(selected = hiz == h, onClick = { hiz = h }, label = { Text("${hizYaz(h)}x") })
                        }
                    }
                    Text("Yavaşlatınca ton değişmez; sadece tempo düşer. Seçtiğin hız sonraki videolarda da kalır.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Repeat, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(8.dp))
                        Text("A-B tekrar", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = aIsaretle) { Text("A: ${a?.let { sureYaz(it) } ?: "işaretle"}") }
                        OutlinedButton(onClick = bIsaretle) { Text("B: ${b?.let { sureYaz(it) } ?: "işaretle"}") }
                        if (a != null || b != null) {
                            IconButton(onClick = { a = null; b = null }) { Icon(Icons.Filled.Clear, "Döngüyü kapat") }
                        }
                    }
                    val bas = a
                    val son = b
                    Text(
                        if (bas != null && son != null) "Döngü açık: ${sureYaz(bas)} → ${sureYaz(son)} arası tekrar çalıyor."
                        else "Zor bir pasajın başında A'ya, sonunda B'ye bas; o bölüm sürekli tekrar çalsın.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (dokumanlar.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Bu türkünün notaları", style = MaterialTheme.typography.titleMedium)
                        Text("Dokununca nota videonun altında açılır; video çalmaya devam eder.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        dokumanlar.forEach { d ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (d.tip == com.baglamaarsivim.data.DokumanTipi.WORD) { oynatici.pause(); dokumanAc(d) }
                                    else acikNotaId = d.id
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(dokumanIkonu(d.tip), null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.size(12.dp))
                                Text(d.dosyaAdi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun sonrakiHiz(h: Float): Float = HIZLAR[(HIZLAR.indexOf(h) + 1).mod(HIZLAR.size)]

private fun hizYaz(h: Float): String = if (h == h.toInt().toFloat()) "${h.toInt()}.0" else h.toString()

@Composable
private fun OynaticiGorunumu(oynatici: ExoPlayer, modifier: Modifier) {
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = true
                keepScreenOn = true // pratik yaparken ekran kapanmasın
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        update = { it.player = oynatici },
        onRelease = { it.player = null },
        modifier = modifier
    )
}
