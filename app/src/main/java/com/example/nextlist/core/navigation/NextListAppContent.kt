package com.example.nextlist.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.nextlist.data.firebase.FirebaseRuntimeStatus
import com.example.nextlist.feature.activityfeed.ActivityFeedScreen
import com.example.nextlist.feature.groups.GroupsScreen
import com.example.nextlist.feature.profile.ProfileScreen

@Composable
fun NextListAppContent(
    firebaseStatus: FirebaseRuntimeStatus,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
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
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.GROUPS.route,
            ) {
                composable(TopLevelDestination.GROUPS.route) {
                    GroupsScreen(firebaseStatus = firebaseStatus)
                }
                composable(TopLevelDestination.ACTIVITY_FEED.route) {
                    ActivityFeedScreen()
                }
                composable(TopLevelDestination.PROFILE.route) {
                    ProfileScreen()
                }
            }
        }
    }
}
