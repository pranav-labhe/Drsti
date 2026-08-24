package com.pranav.drsti.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.drsti.R

/**
 * 512x512 Square Store Icon
 */
@Preview(widthDp = 512, heightDp = 512)
@Composable
fun PlayStoreIconPNG() {
    Box(
        modifier = Modifier
            .size(512.dp)
            .background(Color(0xFF1B1035))
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 1024x500 Play Store Feature Graphic
 */
@Preview(widthDp = 1024, heightDp = 500)
@Composable
fun PlayStoreFeatureGraphic() {
    Box(
        modifier = Modifier
            .size(width = 1024.dp, height = 500.dp)
            .background(Color(0xFF1B1035))
    ) {
        // 1. Vector Geometry & Subtle Background lines
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_banner),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Text Overlay (Reconstructed using Compose)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 80.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "दृष्टि",
                color = Color(0xFFF5A623),
                fontSize = 110.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "SEE BEYOND THE OBVIOUS.",
                color = Color(0xFFF5A623),
                fontSize = 28.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(24.dp))
            
            // Decorative line
            Box(
                modifier = Modifier
                    .width(400.dp)
                    .height(1.dp)
                    .background(Color(0xFFF5A623).copy(alpha = 0.3f))
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Uncover different perspectives through",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 24.sp,
                fontStyle = FontStyle.Italic
            )
            Text(
                text = "hidden clues and deeper signals.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 24.sp,
                fontStyle = FontStyle.Italic
            )
        }
    }
}
