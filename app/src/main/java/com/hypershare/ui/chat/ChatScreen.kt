package com.hypershare.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary
import com.hypershare.ui.theme.WarningAmber

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val displayMessages = if (uiState.messages.isNotEmpty()) uiState.messages else listOf(
        ChatMessageItem("1", "local", "__MY_MESSAGE__", isOutgoing = true),
        ChatMessageItem("2", "peer", "__THEIR_MESSAGE__", isOutgoing = false),
        ChatMessageItem("3", "local", "__MY_PARAGRAPHIC_\nSTYLE_LONG_MESSAGE__", isOutgoing = true)
    )

    val listState = rememberLazyListState()

    // Auto-scroll to bottom message when opening screen or typing
    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    // Single Root Column — Ensures top header remains pinned while LazyColumn resizes dynamically
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
    ) {
        // 1. Status Bar Spacer Block
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(BackgroundBase)
        )

        // 2. Fixed Top Header Bar (Pinned at top of screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(56.dp)
                .background(Color(0xFF666666), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black, CircleShape)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", color = Color.White, fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "USER_NAME",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "⋮",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp
                )
            }
        }

        // 3. Conversation Date Header
        Text(
            text = "__DATE_OF_CONVERSATION__",
            color = WarningAmber,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // 4. Dynamically Shrinking Messages List (weight 1f resizes height when keyboard appears)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayMessages, key = { it.id }) { msg ->
                WireframeChatBubble(message = msg)
            }
        }

        // 5. Bottom Input Bar — Lifts directly above software keyboard using WindowInsets.ime
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("📷", color = TextPrimary, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(6.dp))

            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = { viewModel.updateInputText(it) },
                placeholder = { Text("Start Typing...", color = SignalBlue, fontSize = 14.sp) },
                trailingIcon = {
                    Text(
                        text = "🔗",
                        color = SignalBlue,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SignalBlue,
                    unfocusedBorderColor = SignalBlue,
                    focusedContainerColor = SignalBlue.copy(alpha = 0.15f),
                    unfocusedContainerColor = SignalBlue.copy(alpha = 0.15f)
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = { viewModel.sendMessage() },
                colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                shape = CircleShape,
                modifier = Modifier.size(46.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text("➢", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WireframeChatBubble(message: ChatMessageItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!message.isOutgoing) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", color = Color.White, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .background(
                        color = if (message.isOutgoing) SignalBlue else ConnectedGreen,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Text(
            text = "_TIME_",
            color = TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp, start = if (message.isOutgoing) 0.dp else 40.dp)
        )
    }
}
