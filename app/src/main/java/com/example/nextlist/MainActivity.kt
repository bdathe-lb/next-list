package com.example.nextlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.nextlist.core.designsystem.NextListTheme
import com.example.nextlist.core.navigation.NextListAppContent
import com.example.nextlist.data.firebase.FirebaseEmulatorConnector
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var firebaseEmulatorConnector: FirebaseEmulatorConnector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NextListTheme {
                NextListAppContent(firebaseStatus = firebaseEmulatorConnector.status)
            }
        }
    }
}
