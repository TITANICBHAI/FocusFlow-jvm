package com.focusflow.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.ui.theme.OnSurface
import com.focusflow.ui.theme.Purple60

@Composable
fun FocusFlowLogo(
    size: Dp = 32.dp,
    showText: Boolean = true,
    textColor: Color = OnSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LogoMark(size = size)
        if (showText) {
            Column(verticalArrangement = Arrangement.spacedBy((-4).dp)) {
                Text(
                    text = "FocusFlow",
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.55f).sp,
                    color = textColor,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = "by TBTechs",
                    fontSize = (size.value * 0.28f).sp,
                    color = Purple60,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
fun LogoMark(size: Dp = 32.dp) {
    Image(
        painter = painterResource("focusflow_logo_nobg.png"),
        contentDescription = "FocusFlow logo",
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape((size.value * 0.22f).dp))
    )
}
