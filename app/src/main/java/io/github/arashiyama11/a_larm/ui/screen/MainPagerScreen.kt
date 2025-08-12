package io.github.arashiyama11.a_larm.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.arashiyama11.a_larm.ui.screen.home.HomeScreen
import io.github.arashiyama11.a_larm.ui.screen.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class MainTab(val title: String) { Home("ホーム"), Settings("設定") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPagerScreen(
    navigateToApiKeySetting: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { MainTab.entries.size })
    val scope = rememberCoroutineScope()

    Scaffold() { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
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
                        HomeScreen()
                    }

                    MainTab.Settings -> {
                        SettingsScreen(navigateToApiKeySetting = navigateToApiKeySetting)
                    }
                }
            }
        }
    }
}