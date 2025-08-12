package io.github.arashiyama11.a_larm.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.arashiyama11.a_larm.ui.screen.home.HomeScreen
import io.github.arashiyama11.a_larm.ui.screen.home.HomeViewModel
import io.github.arashiyama11.a_larm.ui.screen.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class MainTab(val title: String) { Home("ホーム"), Settings("設定") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPagerScreen() {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { MainTab.entries.size })
    val scope = rememberCoroutineScope()

    Column {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            MainTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.title) }
                )
            }
        }
        HorizontalPager(state = pagerState) { page ->
            when (MainTab.entries[page]) {
                MainTab.Home -> {
                    val vm: HomeViewModel = hiltViewModel()
                    HomeScreen(
                        state = vm.uiState,
                        onToggleEnabled = vm::onToggleEnabled,
                        // onChangeRoutineMode = vm::setRoutineMode,
                    )
                }

                MainTab.Settings -> {
                    SettingsScreen()
                }
            }
        }
    }
}
