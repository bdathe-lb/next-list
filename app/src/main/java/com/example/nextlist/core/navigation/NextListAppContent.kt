package com.example.nextlist.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.data.firebase.FirebaseRuntimeStatus
import com.example.nextlist.domain.model.AccountSession
import com.example.nextlist.feature.activityfeed.ActivityFeedScreen
import com.example.nextlist.feature.auth.AccountSessionViewModel
import com.example.nextlist.feature.auth.AuthNavHost
import com.example.nextlist.feature.groups.GroupsScreen
import com.example.nextlist.feature.profile.CompleteProfileRoute
import com.example.nextlist.feature.profile.EditProfileRoute
import com.example.nextlist.feature.profile.ProfileRoute

private const val EDIT_PROFILE_ROUTE = "profile/edit"

@Composable
fun NextListAppContent(
    firebaseStatus: FirebaseRuntimeStatus,
    modifier: Modifier = Modifier,
    viewModel: AccountSessionViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    when (val current = session) {
        AccountSession.Loading -> CenteredLoading(modifier)
        AccountSession.SignedOut -> AuthNavHost(
            firebaseStatus = firebaseStatus,
            modifier = modifier,
        )
        is AccountSession.NeedsProfile -> CompleteProfileRoute(
            user = current.user,
            modifier = modifier,
        )
        is AccountSession.SignedIn -> SignedInApp(
            session = current,
            modifier = modifier,
        )
        is AccountSession.Error -> SessionError(
            message = current.kind.toUserMessage(),
            onRetry = viewModel::retry,
            modifier = modifier,
        )
    }
}

@Composable
private fun SignedInApp(
    session: AccountSession.SignedIn,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Text(
                                    text = destination.glyph,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.GROUPS.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.GROUPS.route) {
                GroupsScreen()
            }
            composable(TopLevelDestination.ACTIVITY_FEED.route) {
                ActivityFeedScreen()
            }
            composable(TopLevelDestination.PROFILE.route) {
                ProfileRoute(
                    session = session,
                    onEditProfile = { navController.navigate(EDIT_PROFILE_ROUTE) },
                )
            }
            composable(EDIT_PROFILE_ROUTE) {
                EditProfileRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text("正在恢复登录状态…")
        }
    }
}

@Composable
private fun SessionError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "暂时无法加载账号",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}
