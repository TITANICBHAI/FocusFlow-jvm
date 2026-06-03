package com.focusflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.*
import kotlinx.coroutines.delay

// ── Style ─────────────────────────────────────────────────────────────────────
// Bold purple thumb, 6 dp wide — clearly visible when shown.

@Composable
private fun ffStyle() = LocalScrollbarStyle.current.copy(
    thickness     = 6.dp,
    minimalHeight = 48.dp,
    shape         = RoundedCornerShape(3.dp),
    unhoverColor  = Purple80.copy(alpha = 0.55f),
    hoverColor    = Purple80.copy(alpha = 0.90f)
)

// ── Auto-hide alpha ───────────────────────────────────────────────────────────
// Returns 1f while scrolling (or just after), fades to 0f after 1.6 s of idle.

@Composable
private fun scrollbarAlpha(isScrollInProgress: Boolean): Float {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            visible = true
        } else {
            delay(1600L)
            visible = false
        }
    }
    return animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label         = "sbAlpha"
    ).value
}

// ── Public drop-in replacements ───────────────────────────────────────────────

/** Auto-hiding, bold vertical scrollbar for a [ScrollState]. */
@Composable
fun FfVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val alpha = scrollbarAlpha(scrollState.isScrollInProgress)
    VerticalScrollbar(
        adapter  = rememberScrollbarAdapter(scrollState),
        modifier = modifier.alpha(alpha),
        style    = ffStyle()
    )
}

/** Auto-hiding, bold vertical scrollbar for a [LazyListState]. */
@Composable
fun FfVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val alpha = scrollbarAlpha(listState.isScrollInProgress)
    VerticalScrollbar(
        adapter  = rememberScrollbarAdapter(listState),
        modifier = modifier.alpha(alpha),
        style    = ffStyle()
    )
}

/** Auto-hiding, bold vertical scrollbar for a [LazyGridState]. */
@Composable
fun FfVerticalScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val alpha = scrollbarAlpha(gridState.isScrollInProgress)
    VerticalScrollbar(
        adapter  = rememberScrollbarAdapter(gridState),
        modifier = modifier.alpha(alpha),
        style    = ffStyle()
    )
}

/** Auto-hiding, bold horizontal scrollbar for a [ScrollState]. */
@Composable
fun FfHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val alpha = scrollbarAlpha(scrollState.isScrollInProgress)
    HorizontalScrollbar(
        adapter  = rememberScrollbarAdapter(scrollState),
        modifier = modifier.alpha(alpha),
        style    = ffStyle()
    )
}

// ── Layout helpers (updated to use FfVerticalScrollbar) ───────────────────────

/**
 * A Column that shows an auto-hiding [FfVerticalScrollbar] alongside content.
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
        FfVerticalScrollbar(
            scrollState = scrollState,
            modifier    = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

/** Wraps any LazyColumn with an auto-hiding [FfVerticalScrollbar]. */
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

/**
 * A Box with both vertical + horizontal auto-hiding scrollbars.
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
