package com.baglamaarsivim

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.baglamaarsivim.data.DokumanTipi
import com.baglamaarsivim.ui.AnaEkran
import com.baglamaarsivim.ui.ArsivViewModel
import com.baglamaarsivim.ui.BaglamaTema
import com.baglamaarsivim.ui.DokumanEkrani
import com.baglamaarsivim.ui.IceAktarEkrani
import com.baglamaarsivim.ui.IslemDialog
import com.baglamaarsivim.ui.OynaticiEkrani
import com.baglamaarsivim.ui.TurkuDetayEkrani
import com.baglamaarsivim.util.disaridaAc
import com.baglamaarsivim.util.uzantidanMime

class MainActivity : ComponentActivity() {
    private val vm: ArsivViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) paylasimiIsle(intent)
        setContent { BaglamaTema { Uygulama(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        paylasimiIsle(intent)
    }

    private fun paylasimiIsle(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND || i?.action == Intent.ACTION_SEND_MULTIPLE) {
            vm.paylasimAl(i)
            setIntent(Intent(this, MainActivity::class.java)) // tekrar işlenmesin
        }
    }
}

@Composable
private fun Uygulama(vm: ArsivViewModel) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val gelen by vm.gelen.collectAsState()
    val islem by vm.islem.collectAsState()

    LaunchedEffect(Unit) {
        vm.mesajlar.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
    }
    val gelenVar = gelen != null
    LaunchedEffect(gelenVar) {
        if (gelenVar && nav.currentDestination?.route != "iceaktar") {
            nav.navigate("iceaktar") { launchSingleTop = true }
        }
    }

    val dokumanAc: (Long, DokumanTipi, String) -> Unit = { id, tip, dosya ->
        if (tip == DokumanTipi.WORD) {
            if (!disaridaAc(ctx, java.io.File(vm.repo.dokDir, dosya), uzantidanMime(dosya))) nav.navigate("dokuman/$id")
        } else {
            nav.navigate("dokuman/$id")
        }
    }

    NavHost(navController = nav, startDestination = "ana") {
        composable("ana") {
            AnaEkran(
                vm,
                turkuAc = { nav.navigate("turku/$it") },
                videoAc = { nav.navigate("oynat/$it") },
                dokumanAc = { o -> dokumanAc(o.dokuman.id, o.dokuman.tip, o.dokuman.dosya) }
            )
        }
        composable("turku/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { giris ->
            val id = giris.arguments?.getLong("id") ?: 0L
            TurkuDetayEkrani(
                vm, id,
                geri = { nav.popBackStack() },
                videoAc = { nav.navigate("oynat/$it") },
                dokumanAc = { d -> dokumanAc(d.id, d.tip, d.dosya) }
            )
        }
        composable("oynat/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { giris ->
            OynaticiEkrani(
                vm, giris.arguments?.getLong("id") ?: 0L,
                geri = { nav.popBackStack() },
                dokumanAc = { d -> dokumanAc(d.id, d.tip, d.dosya) }
            )
        }
        composable("dokuman/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { giris ->
            DokumanEkrani(vm, giris.arguments?.getLong("id") ?: 0L, geri = { nav.popBackStack() })
        }
        composable("iceaktar") {
            IceAktarEkrani(vm) { turkuId ->
                nav.popBackStack("ana", inclusive = false)
                if (turkuId != null) nav.navigate("turku/$turkuId")
            }
        }
    }

    islem?.let { IslemDialog(it) }
}
