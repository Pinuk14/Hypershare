package com.hypershare.ui.routingdebug

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
fun RoutingDebugScreen(
    viewModel: RoutingDebugViewModel,
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
                text = "Mesh Routing Table Visualizer",
                style = MaterialTheme.typography.displayLarge
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Dev Debug Build Routing Graph",
                    color = TextSecondary
                )
            }
        }
    }
}
