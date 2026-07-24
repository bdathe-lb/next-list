package com.example.nextlist.data.firebase

import android.content.Context
import com.example.nextlist.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

enum class FirebaseRuntimeStatus {
    NOT_CONFIGURED,
    PRODUCTION,
    EMULATOR,
}

@Singleton
class FirebaseEmulatorConnector @Inject constructor() {
    private val initialized = AtomicBoolean(false)

    var status: FirebaseRuntimeStatus = FirebaseRuntimeStatus.NOT_CONFIGURED
        private set

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        if (!BuildConfig.FIREBASE_CONFIGURED) {
            status = FirebaseRuntimeStatus.NOT_CONFIGURED
            return
        }

        val app = FirebaseApp.initializeApp(context)
            ?: FirebaseApp.getApps(context).firstOrNull()
            ?: return

        if (!BuildConfig.USE_FIREBASE_EMULATORS) {
            status = FirebaseRuntimeStatus.PRODUCTION
            return
        }

        val host = BuildConfig.FIREBASE_EMULATOR_HOST
        FirebaseAuth.getInstance(app).useEmulator(host, 9099)
        FirebaseFirestore.getInstance(app).useEmulator(host, 8080)
        FirebaseFunctions.getInstance(app, BuildConfig.FIREBASE_FUNCTIONS_REGION)
            .useEmulator(host, 5001)
        FirebaseStorage.getInstance(app).useEmulator(host, 9199)
        status = FirebaseRuntimeStatus.EMULATOR
    }
}
