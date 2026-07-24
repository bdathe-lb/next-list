package com.example.nextlist.core.navigation

import android.net.Uri
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.data.firebase.FirebaseRuntimeStatus
import com.example.nextlist.domain.model.AccountSession
import com.example.nextlist.feature.activityfeed.ActivityFeedScreen
import com.example.nextlist.feature.auth.AccountSessionViewModel
import com.example.nextlist.feature.auth.AuthNavHost
import com.example.nextlist.feature.groups.CreateGroupRoute
import com.example.nextlist.feature.groups.GroupDetailRoute
import com.example.nextlist.feature.groups.GroupSettingsRoute
import com.example.nextlist.feature.groups.GroupsRoute
import com.example.nextlist.feature.groups.InviteRoute
import com.example.nextlist.feature.groups.JoinCodeScreen
import com.example.nextlist.feature.groups.JoinGroupRoute
import com.example.nextlist.feature.groups.MembersRoute
import com.example.nextlist.feature.groups.PendingInviteViewModel
import com.example.nextlist.feature.ideas.IdeaDetailRoute
import com.example.nextlist.feature.ideas.IdeaFormRoute
import com.example.nextlist.feature.profile.CompleteProfileRoute
import com.example.nextlist.feature.profile.EditProfileRoute
import com.example.nextlist.feature.profile.ProfileRoute
import kotlinx.coroutines.launch

private const val EDIT_PROFILE_ROUTE = "profile/edit"
private const val CREATE_GROUP_ROUTE = "group/create"
private const val JOIN_CODE_ROUTE = "group/join-code"
private const val JOIN_GROUP_ROUTE = "group/join?kind={kind}&value={value}"
private const val GROUP_DETAIL_ROUTE = "group/{groupId}"
private const val GROUP_MEMBERS_ROUTE = "group/{groupId}/members"
private const val GROUP_INVITE_ROUTE = "group/{groupId}/invite"
private const val GROUP_SETTINGS_ROUTE = "group/{groupId}/settings"
private const val NEW_IDEA_ROUTE = "group/{groupId}/idea/new"
private const val IDEA_DETAIL_ROUTE = "group/{groupId}/idea/{ideaId}"
private const val EDIT_IDEA_ROUTE = "group/{groupId}/idea/{ideaId}/edit"

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
    pendingInviteViewModel: PendingInviteViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()
    val pendingInvite by pendingInviteViewModel.pendingInvite.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pendingInvite) {
        pendingInvite?.let { invite ->
            val kind = invite.kind.name.lowercase()
            navController.navigate(
                "group/join?kind=$kind&value=${Uri.encode(invite.value)}",
            ) {
                launchSingleTop = true
            }
        }
    }

    fun showMessageAndReturn(message: String) {
        navController.popBackStack(TopLevelDestination.GROUPS.route, inclusive = false)
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                GroupsRoute(
                    onCreateGroup = { navController.navigate(CREATE_GROUP_ROUTE) },
                    onJoinGroup = { navController.navigate(JOIN_CODE_ROUTE) },
                    onOpenGroup = { groupId ->
                        navController.navigate("group/${Uri.encode(groupId)}")
                    },
                )
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
            composable(CREATE_GROUP_ROUTE) {
                CreateGroupRoute(
                    onBack = { navController.popBackStack() },
                    onVerifyEmail = {
                        navController.navigate(TopLevelDestination.PROFILE.route)
                    },
                    onCreated = { groupId ->
                        navController.navigate("group/${Uri.encode(groupId)}") {
                            popUpTo(CREATE_GROUP_ROUTE) { inclusive = true }
                        }
                    },
                )
            }
            composable(JOIN_CODE_ROUTE) {
                JoinCodeScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = { code ->
                        navController.navigate(
                            "group/join?kind=code&value=${Uri.encode(code)}",
                        )
                    },
                )
            }
            composable(
                route = JOIN_GROUP_ROUTE,
                arguments = listOf(
                    navArgument("kind") {
                        type = NavType.StringType
                        defaultValue = "code"
                    },
                    navArgument("value") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                JoinGroupRoute(
                    onBack = { navController.popBackStack() },
                    onVerifyEmail = {
                        navController.navigate(TopLevelDestination.PROFILE.route)
                    },
                    onJoined = { groupId ->
                        navController.navigate("group/${Uri.encode(groupId)}") {
                            popUpTo(TopLevelDestination.GROUPS.route)
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = GROUP_DETAIL_ROUTE,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) {
                GroupDetailRoute(
                    onBack = { navController.popBackStack() },
                    onMembers = { groupId ->
                        navController.navigate("group/${Uri.encode(groupId)}/members")
                    },
                    onInvite = { groupId ->
                        navController.navigate("group/${Uri.encode(groupId)}/invite")
                    },
                    onSettings = { groupId ->
                        navController.navigate("group/${Uri.encode(groupId)}/settings")
                    },
                    onAddIdea = { groupId ->
                        navController.navigate("group/${Uri.encode(groupId)}/idea/new")
                    },
                    onOpenIdea = { groupId, ideaId ->
                        navController.navigate(
                            "group/${Uri.encode(groupId)}/idea/${Uri.encode(ideaId)}",
                        )
                    },
                    onAccessLost = ::showMessageAndReturn,
                )
            }
            composable(
                route = NEW_IDEA_ROUTE,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) {
                IdeaFormRoute(
                    onBack = { navController.popBackStack() },
                    onSaved = { ideaId ->
                        val groupId = it.arguments?.getString("groupId").orEmpty()
                        navController.navigate(
                            "group/${Uri.encode(groupId)}/idea/${Uri.encode(ideaId)}",
                        ) {
                            popUpTo(NEW_IDEA_ROUTE) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = IDEA_DETAIL_ROUTE,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.StringType },
                    navArgument("ideaId") { type = NavType.StringType },
                ),
            ) {
                IdeaDetailRoute(
                    onBack = { navController.popBackStack() },
                    onEdit = { groupId, ideaId ->
                        navController.navigate(
                            "group/${Uri.encode(groupId)}/idea/${Uri.encode(ideaId)}/edit",
                        )
                    },
                )
            }
            composable(
                route = EDIT_IDEA_ROUTE,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.StringType },
                    navArgument("ideaId") { type = NavType.StringType },
                ),
            ) {
                IdeaFormRoute(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = GROUP_MEMBERS_ROUTE,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) {
                MembersRoute(
                    onBack = { navController.popBackStack() },
                    onExitGroup = ::showMessageAndReturn,
                )
            }
            composable(
                route = GROUP_INVITE_ROUTE,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) {
                InviteRoute(onBack = { navController.popBackStack() })
            }
            composable(
                route = GROUP_SETTINGS_ROUTE,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) {
                GroupSettingsRoute(
                    onBack = { navController.popBackStack() },
                    onExitGroup = ::showMessageAndReturn,
                )
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
