package com.focusflow.ui.components

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.*

// ── Style ──────────────────────────────────────────────────────────────────────
// unhoverColor = subtle track always visible so users know they can scroll.
// hoverColor   = full purple when the cursor moves near — handled natively by
//                Compose Desktop's VerticalScrollbar without any alpha modifier.
// While scrolling, unhoverColor is boosted so the thumb is clearly visible.
//
// NO alpha modifier is used — that was hiding the native hover behaviour.

@Composable
private fun ffStyle(isScrollInProgress: Boolean) = LocalScrollbarStyle.current.copy(
    thickness     = 8.dp,
    minimalHeight = 48.dp,
    shape         = RoundedCornerShape(4.dp),
    unhoverColor  = Purple80.copy(alpha = if (isScrollInProgress) 0.75f else 0.22f),
    hoverColor    = Purple80.copy(alpha = 0.95f)
)

// ── Public drop-in replacements ────────────────────────────────────────────────

/** Vertical scrollbar for a [ScrollState].
 *  - Subtle at rest so users see the track and know the screen scrolls.
 *  - Lights up instantly when the cursor moves near (native Compose Desktop hover).
 *  - Bright while scrolling.
 */
@Composable
fun FfVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier,
        style    = ffStyle(scrollState.isScrollInProgress)
    )
}

/** Vertical scrollbar for a [LazyListState]. */
@Composable
fun FfVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        modifier = modifier,
        style    = ffStyle(listState.isScrollInProgress)
    )
}

/** Vertical scrollbar for a [LazyGridState]. */
@Composable
fun FfVerticalScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(gridState),
        modifier = modifier,
        style    = ffStyle(gridState.isScrollInProgress)
    )
}

/** Horizontal scrollbar for a [ScrollState]. */
@Composable
fun FfHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier,
        style    = ffStyle(scrollState.isScrollInProgress)
    )
}

// ── Layout helpers ─────────────────────────────────────────────────────────────

/** Column with a vertical scrollbar always visible on the right edge. */
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
        FfVerticalScrollbar(
            scrollState = scrollState,
            modifier    = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

/** Box wrapping a LazyColumn with a vertical scrollbar always visible on the right edge. */
@Composable
fun LazyScrollbarBox(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        content()
        FfVerticalScrollbar(
            listState = lazyListState,
            modifier  = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

/** Box with both vertical and horizontal scrollbars. */
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
        FfVerticalScrollbar(
            scrollState = vScrollState,
            modifier    = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(bottom = 16.dp)
        )
        FfHorizontalScrollbar(
            scrollState = hScrollState,
            modifier    = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(end = 16.dp)
        )
    }
}
