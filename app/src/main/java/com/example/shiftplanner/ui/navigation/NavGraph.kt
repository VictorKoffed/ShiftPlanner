package com.example.shiftplanner.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.shiftplanner.ScheduleViewModel
import com.example.shiftplanner.ui.screens.HomeScreen
import com.example.shiftplanner.ui.screens.ScheduleScreen
import com.example.shiftplanner.ui.screens.TestGridScreen
import com.example.shiftplanner.ui.screens.MonthViewScreen
import com.example.shiftplanner.ui.screens.ColleaguesScreen
import com.example.shiftplanner.ui.screens.StatisticsScreen
import com.example.shiftplanner.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

// Represents all distinct destinations in the app
sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Idag")
    object MonthView : Screen("month_view", "Månadsvy")
    object Schedule : Screen("schedule", "Mata in pass")
    object TestGrid : Screen("test_grid", "Granska schema")
    object Colleagues : Screen("colleagues", "Hantera kollegor")
    object Statistics : Screen("statistics", "Sammanställning & Statistik")
    object Settings : Screen("settings", "Inställningar")
}

@Composable
fun NavGraph(viewModel: ScheduleViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            MainLayout(navController = navController) {
                SwipeableHomeAndMonthScreen(viewModel, navController, initialPage = 0)
            }
        }
        composable(Screen.MonthView.route) {
            MainLayout(navController = navController) {
                SwipeableHomeAndMonthScreen(viewModel, navController, initialPage = 1)
            }
        }
        composable(Screen.Schedule.route) {
            MainLayout(navController = navController) {
                ScheduleScreen(viewModel, navController)
            }
        }
        composable(Screen.TestGrid.route) {
            MainLayout(navController = navController) {
                TestGridScreen(viewModel, navController)
            }
        }
        composable(Screen.Colleagues.route) {
            MainLayout(navController = navController) {
                ColleaguesScreen(viewModel)
            }
        }
        composable(Screen.Statistics.route) {
            MainLayout(navController = navController) {
                StatisticsScreen(viewModel, navController)
            }
        }
        composable(Screen.Settings.route) {
            MainLayout(navController = navController) {
                SettingsScreen(viewModel, navController)
            }
        }
    }
}

// Handles the swipable tab layout containing the Home and Month views
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableHomeAndMonthScreen(viewModel: ScheduleViewModel, navController: NavController, initialPage: Int) {
    val pagerState = rememberPagerState(initialPage = initialPage) { 2 }
    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf(Screen.Home, Screen.MonthView)

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, screen ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(screen.title, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.systemBars)
            ) {
                when (page) {
                    0 -> HomeScreen(viewModel, navController)
                    1 -> MonthViewScreen(viewModel, navController)
                }
            }
        }
    }
}

// Shared scaffold wrapper providing the TopAppBar and Navigation Drawer
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(
    navController: NavController,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val route = navBackStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "ShiftPlanner",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                NavigationDrawerItem(
                    label = { Text("🏠 Gå till Start / Idag") },
                    selected = route == Screen.Home.route,
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (route != Screen.Home.route) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("📊 Sammanställning & Statistik") },
                    selected = route == Screen.Statistics.route,
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (route != Screen.Statistics.route) {
                            navController.navigate(Screen.Statistics.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("📋 Granska hela schemat") },
                    selected = route == Screen.TestGrid.route,
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (route != Screen.TestGrid.route) {
                            navController.navigate(Screen.TestGrid.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    label = { Text("⚙️ Inställningar") },
                    selected = route == Screen.Settings.route,
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (route != Screen.Settings.route) {
                            navController.navigate(Screen.Settings.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ShiftPlanner") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Meny")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                content(paddingValues)
            }
        }
    }
}