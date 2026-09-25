package com.baglamaarsivim

import android.app.Application
import com.baglamaarsivim.data.AppDatabase
import com.baglamaarsivim.data.ArsivRepository
import com.baglamaarsivim.work.Bildirimler

class BaglamaApp : Application() {
    lateinit var repo: ArsivRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = ArsivRepository(this, AppDatabase.get(this))
        repo.gelenKlasorunuTemizle()
        Bildirimler.kanalOlustur(this)
    }
}
