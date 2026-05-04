package com.focusflow.ui.components

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A Column that shows a VerticalScrollbar alongside scrollable content.
 * Drop-in replacement for Column + verticalScroll.
 */
@Composable
fun ScrollbarColumn(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding),
            content = content
        )
        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}

/**
 * Wraps any LazyColumn with a VerticalScrollbar.
 */
@Composable
fun LazyScrollbarBox(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        content()
        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            adapter = rememberScrollbarAdapter(lazyListState)
        )
    }
}

/**
 * A Box that provides both vertical + horizontal scrollbars.
 * Use when content can exceed either dimension.
 */
@Composable
fun DualScrollbarBox(
    modifier: Modifier = Modifier,
    vScrollState: ScrollState,
    hScrollState: ScrollState,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(hScrollState)
                .verticalScroll(vScrollState)
        ) {
            content()
        }
        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(bottom = 16.dp),
            adapter = rememberScrollbarAdapter(vScrollState)
        )
        HorizontalScrollbar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(end = 16.dp),
            adapter = rememberScrollbarAdapter(hScrollState)
        )
    }
}
