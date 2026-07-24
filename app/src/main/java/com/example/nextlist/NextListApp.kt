package com.example.nextlist

import android.app.Application
import com.example.nextlist.data.firebase.FirebaseEmulatorConnector
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NextListApp : Application() {
    @Inject
    lateinit var firebaseEmulatorConnector: FirebaseEmulatorConnector

    override fun onCreate() {
        super.onCreate()
        firebaseEmulatorConnector.initialize(this)
    }
}
