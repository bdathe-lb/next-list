package com.example.nextlist.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private data class OssLibrary(val name: String, val license: String)

private val OSS_LIBRARIES = listOf(
    OssLibrary("Jetpack Compose", "Apache License 2.0"),
    OssLibrary("AndroidX (Activity, Lifecycle, Navigation, DataStore, WorkManager)", "Apache License 2.0"),
    OssLibrary("Hilt / Dagger", "Apache License 2.0"),
    OssLibrary("Kotlin Coroutines", "Apache License 2.0"),
    OssLibrary("Firebase Android SDK", "Apache License 2.0"),
    OssLibrary("Coil", "Apache License 2.0"),
    OssLibrary("AndroidX ExifInterface", "Apache License 2.0"),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OssLicensesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("开源许可") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "下次使用了以下开源组件，谨此致谢。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OSS_LIBRARIES.forEach { library ->
                Column(
                    modifier = Modifier.semantics {
                        contentDescription = "${library.name}，许可 ${library.license}"
                    },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(library.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        library.license,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
