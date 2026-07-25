package com.hypershare.ui.stream

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.TextSecondary

@Composable
fun StreamPlayerScreen(
    viewModel: StreamPlayerViewModel,
    onBackClick: () -> Unit
) {
    Scaffold(
        containerColor = BackgroundBase
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "In-Memory Stream Player",
                style = MaterialTheme.typography.displayLarge
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Stream Player (View-Only / No Disk Storage)",
                    color = TextSecondary
                )
            }
        }
    }
}
