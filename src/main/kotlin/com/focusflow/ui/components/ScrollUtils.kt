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
// Purple thumb, 8 dp wide — always visible at rest, full brightness when active.

@Composable
private fun ffStyle() = LocalScrollbarStyle.current.copy(
    thickness     = 8.dp,
    minimalHeight = 48.dp,
    shape         = RoundedCornerShape(4.dp),
    unhoverColor  = Purple80.copy(alpha = 0.45f),
    hoverColor    = Purple80.copy(alpha = 0.95f)
)

// ── Scrollbar alpha ───────────────────────────────────────────────────────────
// Always visible at rest (0.28f) so the user knows where to grab.
// Animates to 1f while scrolling, then settles back to 0.28f after 1.2 s.

private const val SCROLLBAR_REST_ALPHA   = 0.28f
private const val SCROLLBAR_ACTIVE_ALPHA = 1.00f

@Composable
private fun scrollbarAlpha(isScrollInProgress: Boolean): Float {
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            active = true
        } else {
            delay(1200L)
            active = false
        }
    }
    return animateFloatAsState(
        targetValue   = if (active) SCROLLBAR_ACTIVE_ALPHA else SCROLLBAR_REST_ALPHA,
        animationSpec = tween(350),
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
