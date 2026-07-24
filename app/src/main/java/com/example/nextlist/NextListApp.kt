package com.example.nextlist

import android.app.Application
import com.example.nextlist.data.firebase.FirebaseEmulatorConnector
import com.example.nextlist.data.messaging.MessagingRegistrationCoordinator
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NextListApp : Application() {
    @Inject
    lateinit var firebaseEmulatorConnector: FirebaseEmulatorConnector

    @Inject
    lateinit var messagingRegistrationCoordinator: MessagingRegistrationCoordinator

    override fun onCreate() {
        super.onCreate()
        firebaseEmulatorConnector.initialize(this)
        messagingRegistrationCoordinator.start()
        if (BuildConfig.FIREBASE_CONFIGURED && !BuildConfig.USE_FIREBASE_EMULATORS) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
        }
    }
}
