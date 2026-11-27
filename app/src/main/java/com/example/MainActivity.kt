package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.AuthHelper
import com.example.data.PairlyRepository
import com.example.data.ThemePreferencesRepository
import com.example.data.model.SwipeAction
import com.example.data.model.ThemeMode
import com.example.ui.screens.ChatThreadScreen
import com.example.ui.screens.ChatsScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.MatchesScreen
import com.example.ui.screens.NewMatchCelebrationDialog
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileAndSafetyScreen
import com.example.ui.screens.SplashAndWelcomeScreen
import com.example.ui.theme.PairlyPink
import com.example.ui.theme.PairlyTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.MainSessionViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object PairlyRoutes {
    const val DISCOVER = "discover"
    const val MATCHES = "matches"
    const val CHATS = "chats"
    const val PROFILE = "profile"
    const val CHAT_THREAD = "chat_thread/{matchId}"

    fun chatThread(matchId: String): String = "chat_thread/$matchId"
}

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themeRepo = ThemePreferencesRepository(applicationContext)

        setContent {
            val themeMode by themeRepo.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)
            val scope = rememberCoroutineScope()

            PairlyTheme(themeMode = themeMode) {
                PairlyRootApp(
                    themeMode = themeMode,
                    onChangeThemeMode = { mode ->
                        scope.launch { themeRepo.setThemeMode(mode) }
                    }
                )
            }
        }
    }
}

@Composable
private fun PairlyRootApp(
    themeMode: ThemeMode,
    onChangeThemeMode: (ThemeMode) -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        SplashAndWelcomeScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedPairlyFlow(
                currentUserId = user.uid,
                themeMode = themeMode,
                onChangeThemeMode = onChangeThemeMode,
                onSignedOut = { currentUser = Firebase.auth.currentUser }
            )
        }
    }
}

@Composable
private fun AuthenticatedPairlyFlow(
    currentUserId: String,
    themeMode: ThemeMode,
    onChangeThemeMode: (ThemeMode) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val sessionViewModel: MainSessionViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainSessionViewModel(PairlyRepository(db), currentUserId)
            }
        }
    )

    val myProfileState by sessionViewModel.myProfileState.collectAsStateWithLifecycle()
    val isBusy by sessionViewModel.isBusy.collectAsStateWithLifecycle()
    val actionMessage by sessionViewModel.actionMessage.collectAsStateWithLifecycle()

    val performSignOut: () -> Unit = {
        AuthHelper.signOut(credentialManager, scope, onSignOutComplete = onSignedOut)
    }

    when (val profileState = myProfileState) {
        is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PairlyPink)
            }
        }

        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profileState.message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        is UiState.Success -> {
            val profile = profileState.data
            if (profile == null) {
                OnboardingScreen(
                    isBusy = isBusy,
                    errorMessage = actionMessage,
                    onClearError = sessionViewModel::clearMessage,
                    onSignOut = performSignOut,
                    onComplete = { name, dobIso, city, state, bio, pref, interests, hobbies, langs, prompts, photos ->
                        sessionViewModel.completeOnboarding(
                            displayName = name,
                            dobIso = dobIso,
                            city = city,
                            state = state,
                            bio = bio,
                            relationshipPreference = pref,
                            interests = interests,
                            hobbies = hobbies,
                            languages = langs,
                            prompts = prompts,
                            photos = photos
                        )
                    }
                )
            } else {
                MainAuthenticatedScaffold(
                    currentUserId = currentUserId,
                    sessionViewModel = sessionViewModel,
                    themeMode = themeMode,
                    onChangeThemeMode = onChangeThemeMode,
                    onSignOut = performSignOut
                )
            }
        }
    }
}

@Composable
private fun MainAuthenticatedScaffold(
    currentUserId: String,
    sessionViewModel: MainSessionViewModel,
    themeMode: ThemeMode,
    onChangeThemeMode: (ThemeMode) -> Unit,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val myProfileState by sessionViewModel.myProfileState.collectAsStateWithLifecycle()
    val privateAccountState by sessionViewModel.privateAccountState.collectAsStateWithLifecycle()
    val deckState by sessionViewModel.discoveryDeckState.collectAsStateWithLifecycle()
    val filter by sessionViewModel.discoveryFilter.collectAsStateWithLifecycle()
    val matchesState by sessionViewModel.matchesWithProfilesState.collectAsStateWithLifecycle()
    val likesReceivedState by sessionViewModel.likesReceivedState.collectAsStateWithLifecycle()
    val likesSentState by sessionViewModel.likesSentState.collectAsStateWithLifecycle()
    val blockedUsers by sessionViewModel.blockedUsersProfilesState.collectAsStateWithLifecycle()
    val newMatchCelebration by sessionViewModel.newMatchCelebration.collectAsStateWithLifecycle()
    val actionMessage by sessionViewModel.actionMessage.collectAsStateWithLifecycle()
    val isBusy by sessionViewModel.isBusy.collectAsStateWithLifecycle()

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            sessionViewModel.clearMessage()
        }
    }

    val myProfile = (myProfileState as? UiState.Success)?.data
    val privateAccount = (privateAccountState as? UiState.Success)?.data
    val unreadChatsCount = (matchesState as? UiState.Success)?.data?.count { it.isUnread } ?: 0
    val likesCount = (likesReceivedState as? UiState.Success)?.data?.size ?: 0

    val navTabs = listOf(
        NavTabItem(
            route = PairlyRoutes.DISCOVER,
            labelRes = R.string.nav_discover,
            selectedIcon = Icons.Filled.Explore,
            unselectedIcon = Icons.Outlined.Explore
        ),
        NavTabItem(
            route = PairlyRoutes.MATCHES,
            labelRes = R.string.nav_matches,
            selectedIcon = Icons.Filled.Favorite,
            unselectedIcon = Icons.Outlined.FavoriteBorder,
            badgeCount = likesCount
        ),
        NavTabItem(
            route = PairlyRoutes.CHATS,
            labelRes = R.string.nav_chats,
            selectedIcon = Icons.AutoMirrored.Filled.Chat,
            unselectedIcon = Icons.AutoMirrored.Outlined.Chat,
            badgeCount = unreadChatsCount
        ),
        NavTabItem(
            route = PairlyRoutes.PROFILE,
            labelRes = R.string.nav_profile,
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person
        )
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isInChatThread = currentDestination?.route == PairlyRoutes.CHAT_THREAD

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && !isInChatThread) {
                    NavigationBar {
                        navTabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (tab.badgeCount > 0) {
                                                Badge(containerColor = PairlyPink) {
                                                    Text(tab.badgeCount.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && !isInChatThread) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        navTabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (tab.badgeCount > 0) {
                                                Badge(containerColor = PairlyPink) {
                                                    Text(tab.badgeCount.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = PairlyRoutes.DISCOVER,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(PairlyRoutes.DISCOVER) {
                        DiscoverScreen(
                            myProfile = myProfile,
                            deckState = deckState,
                            currentFilter = filter,
                            onApplyFilter = sessionViewModel::updateFilter,
                            onSwipe = sessionViewModel::swipeOnProfile,
                            onBlockUser = { uid -> sessionViewModel.blockUser(uid, null) },
                            onReportUser = { uid, reason, details ->
                                sessionViewModel.submitReport(uid, reason, details, "profile")
                            }
                        )
                    }

                    composable(PairlyRoutes.MATCHES) {
                        MatchesScreen(
                            matchesState = matchesState,
                            likesReceivedState = likesReceivedState,
                            likesSentState = likesSentState,
                            onOpenChat = { matchId ->
                                navController.navigate(PairlyRoutes.chatThread(matchId))
                            },
                            onLikeBack = { sender ->
                                sessionViewModel.swipeOnProfile(sender, SwipeAction.LIKE)
                            },
                            onPassLike = { sender ->
                                sessionViewModel.swipeOnProfile(sender, SwipeAction.PASS)
                            },
                            onUnmatch = sessionViewModel::unmatch,
                            onBlockUser = sessionViewModel::blockUser,
                            onReportUser = { uid, reason, details ->
                                sessionViewModel.submitReport(uid, reason, details, "profile")
                            }
                        )
                    }

                    composable(PairlyRoutes.CHATS) {
                        ChatsScreen(
                            matchesState = matchesState,
                            onOpenChat = { matchId ->
                                navController.navigate(PairlyRoutes.chatThread(matchId))
                            }
                        )
                    }

                    composable(
                        route = PairlyRoutes.CHAT_THREAD,
                        arguments = listOf(navArgument("matchId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val matchId = backStackEntry.arguments?.getString("matchId").orEmpty()
                        val matchesList = (matchesState as? UiState.Success)?.data.orEmpty()
                        val matchWithProfile = matchesList.firstOrNull { it.match.docId == matchId }

                        val chatViewModel: ChatViewModel = viewModel(
                            key = "${currentUserId}_$matchId",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY]) {
                                        "APPLICATION_KEY missing from CreationExtras"
                                    }
                                    val databaseId = app.getString(R.string.firestore_database_id)
                                    val db = FirebaseFirestore.getInstance(databaseId)
                                    ChatViewModel(PairlyRepository(db), currentUserId, matchId)
                                }
                            }
                        )

                        ChatThreadScreen(
                            chatViewModel = chatViewModel,
                            match = matchWithProfile?.match,
                            partner = matchWithProfile?.partner,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(PairlyRoutes.PROFILE) {
                        ProfileAndSafetyScreen(
                            profile = myProfile,
                            privateAccount = privateAccount,
                            blockedUsers = blockedUsers,
                            themeMode = themeMode,
                            isBusy = isBusy,
                            onChangeThemeMode = onChangeThemeMode,
                            onUpdatePrivacy = sessionViewModel::updatePrivacySettings,
                            onSaveProfileEdits = sessionViewModel::updateProfile,
                            onUnblockUser = sessionViewModel::unblockUser,
                            onSignOut = onSignOut,
                            onDeleteAccount = {
                                sessionViewModel.deleteAccount(onComplete = onSignOut)
                            }
                        )
                    }
                }
            }
        }
    }

    newMatchCelebration?.let { matchedProfile ->
        val sortedPair = listOf(currentUserId, matchedProfile.userId).sorted()
        val matchId = "${sortedPair[0]}_${sortedPair[1]}"
        NewMatchCelebrationDialog(
            matchedProfile = matchedProfile,
            onSendMessage = {
                sessionViewModel.dismissMatchCelebration()
                navController.navigate(PairlyRoutes.chatThread(matchId))
            },
            onKeepSwiping = {
                sessionViewModel.dismissMatchCelebration()
            }
        )
    }
}
