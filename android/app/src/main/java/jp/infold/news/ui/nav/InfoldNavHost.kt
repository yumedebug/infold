package jp.infold.news.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import jp.infold.news.AppViewModel
import jp.infold.news.AuthState
import jp.infold.news.R
import jp.infold.news.ui.LocalSnackbarHostState
import jp.infold.news.ui.account.AccountScreen
import jp.infold.news.ui.article.ArticleDetailScreen
import jp.infold.news.ui.articles.ArticlesScreen
import jp.infold.news.ui.categories.CategoriesScreen
import jp.infold.news.ui.home.HomeScreen
import jp.infold.news.ui.login.LoginScreen
import jp.infold.news.ui.points.PointsScreen
import jp.infold.news.ui.search.SearchScreen
import jp.infold.news.ui.settings.SettingsScreen
import jp.infold.news.ui.theme.LocalInfoldColors
import jp.infold.news.util.categoryDisplayName

// ============================================================
// 画面遷移の定義（Jetpack Compose Navigation）
// 戻るボタンはナビゲーションスタックの自然な挙動で動作する
// ============================================================

private data class TabItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val tabItems = listOf(
    TabItem("home", R.string.nav_home, Icons.Filled.Home),
    TabItem("articles", R.string.nav_articles, Icons.Filled.List),
    TabItem("categories", R.string.nav_categories, Icons.Filled.Menu),
    TabItem("search", R.string.nav_search, Icons.Filled.Search),
    TabItem("account", R.string.nav_account, Icons.Filled.Person),
)

@Composable
fun InfoldNavHost(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 通知タップ / INFOLD リンク → ネイティブの記事詳細へ
    LaunchedEffect(Unit) {
        viewModel.navigateToArticle.collect { articleId ->
            navController.navigate("article/$articleId")
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabItems.map { it.route } ||
        currentRoute?.startsWith("articles") == true

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onSelect = { route -> selectTab(navController, route) },
                )
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding),
            ) {
                composable("home") {
                    HomeScreen(
                        lang = language,
                        categories = categories,
                        onOpenArticle = { id -> navController.navigate("article/$id") },
                        onOpenArticles = { selectTab(navController, "articles") },
                        onOpenCategory = { slug -> openCategory(navController, slug) },
                        onOpenAccount = { selectTab(navController, "account") },
                    )
                }

                composable(
                    route = "articles?category={category}",
                    arguments = listOf(
                        navArgument("category") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                ) { entry ->
                    ArticlesScreen(
                        lang = language,
                        categories = categories,
                        initialCategory = entry.arguments?.getString("category"),
                        onOpenArticle = { id -> navController.navigate("article/$id") },
                    )
                }

                composable("categories") {
                    CategoriesScreen(
                        lang = language,
                        categories = categories,
                        onOpenCategory = { slug -> openCategory(navController, slug) },
                    )
                }

                composable("search") {
                    SearchScreen(
                        lang = language,
                        onOpenArticle = { id -> navController.navigate("article/$id") },
                    )
                }

                composable("account") {
                    AccountScreen(
                        authState = authState,
                        onOpenLogin = { navController.navigate("login") },
                        onOpenPoints = { navController.navigate("points") },
                        onOpenSettings = { navController.navigate("settings") },
                        onLogout = { viewModel.onLogout() },
                    )
                }

                composable("login") {
                    LoginScreen(
                        onBack = { navController.popBackStack() },
                        onLoggedIn = { navController.popBackStack() },
                        onLoginSuccess = { viewModel.onLogin(it) },
                    )
                }

                composable("points") {
                    PointsScreen(
                        authState = authState,
                        lang = language,
                        onBack = { navController.popBackStack() },
                        onOpenLogin = { navController.navigate("login") },
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        language = language,
                        themeMode = themeMode,
                        onBack = { navController.popBackStack() },
                        onSetLanguage = { viewModel.setLanguage(it) },
                        onSetTheme = { viewModel.setTheme(it) },
                        onToggleNotifications = { enabled ->
                            if (!enabled) {
                                val token = jp.infold.news.data.Prefs.getFcmToken(context)
                                if (token != null) viewModel.unregisterPushToken(token)
                            }
                        },
                    )
                }

                composable(
                    route = "article/{articleId}",
                    arguments = listOf(navArgument("articleId") { type = NavType.LongType }),
                ) { entry ->
                    ArticleDetailScreen(
                        articleId = entry.arguments?.getLong("articleId") ?: 0L,
                        lang = language,
                        isLoggedIn = authState is AuthState.LoggedIn,
                        categories = categories,
                        onBack = { navController.popBackStack() },
                        onOpenArticle = { id -> navController.navigate("article/$id") },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val colors = LocalInfoldColors.current
    NavigationBar(containerColor = colors.headerBackground) {
        tabItems.forEach { item ->
            val selected = when {
                item.route == "articles" -> currentRoute?.startsWith("articles") == true
                else -> currentRoute == item.route
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.labelRes)) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.primary,
                    indicatorColor = colors.primary.copy(alpha = 0.15f),
                    unselectedIconColor = colors.textFaint,
                    unselectedTextColor = colors.textFaint,
                ),
            )
        }
    }
}

private fun selectTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun openCategory(navController: NavHostController, slug: String) {
    navController.navigate("articles?category=$slug") {
        launchSingleTop = true
    }
}
