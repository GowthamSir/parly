package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.LikeReceivedItem
import com.example.data.model.LikeSentItem
import com.example.data.model.MatchWithProfile
import com.example.data.model.ReportReason
import com.example.data.model.SwipeAction
import com.example.data.model.UserProfile
import com.example.ui.components.PairlyPhotoImage
import com.example.ui.components.ProfileDetailSheet
import com.example.ui.components.ReportUserDialog
import com.example.ui.theme.PairlyPink
import com.example.ui.theme.PairlyPurple
import com.example.ui.theme.SuperLikeVioletGold
import com.example.ui.viewmodel.UiState

@Composable
fun MatchesScreen(
    matchesState: UiState<List<MatchWithProfile>>,
    likesReceivedState: UiState<List<LikeReceivedItem>>,
    likesSentState: UiState<List<LikeSentItem>>,
    onOpenChat: (String) -> Unit,
    onLikeBack: (UserProfile) -> Unit,
    onPassLike: (UserProfile) -> Unit,
    onUnmatch: (String) -> Unit,
    onBlockUser: (String, String?) -> Unit,
    onReportUser: (String, ReportReason, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var inspectedProfile by remember { mutableStateOf<UserProfile?>(null) }
    var unmatchTarget by remember { mutableStateOf<MatchWithProfile?>(null) }
    var reportTarget by remember { mutableStateOf<UserProfile?>(null) }

    val tabs = listOf("Matches", "Likes You", "Sent")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Connections",
                style = MaterialTheme.typography.headlineLarge
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        modifier = Modifier.testTag("matches_tab_$index")
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    when (matchesState) {
                        is UiState.Loading -> CenteredSpinner()
                        is UiState.Error -> CenteredError(matchesState.message)
                        is UiState.Success -> {
                            val list = matchesState.data
                            if (list.isEmpty()) {
                                EmptyConnectionsState(
                                    title = "No matches yet",
                                    subtitle = "When someone you like also likes you back, they appear right here."
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    items(list, key = { it.match.docId }) { item ->
                                        MatchCardRow(
                                            item = item,
                                            onOpenChat = { onOpenChat(item.match.docId) },
                                            onInspect = { item.partner?.let { inspectedProfile = it } },
                                            onUnmatch = { unmatchTarget = item }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    when (likesReceivedState) {
                        is UiState.Loading -> CenteredSpinner()
                        is UiState.Error -> CenteredError(likesReceivedState.message)
                        is UiState.Success -> {
                            val list = likesReceivedState.data
                            if (list.isEmpty()) {
                                EmptyConnectionsState(
                                    title = "No incoming likes yet",
                                    subtitle = "People who like or Super Like your profile will show up here."
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    items(list, key = { it.swipe.fromUid }) { item ->
                                        val sender = item.sender
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = sender != null) {
                                                    if (sender != null) inspectedProfile = sender
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                PairlyPhotoImage(
                                                    photoSource = sender?.photos?.firstOrNull(),
                                                    fallbackName = sender?.displayName ?: "Member",
                                                    contentDescription = "Like from ${sender?.displayName ?: "member"}",
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .clip(CircleShape)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = sender?.let { "${it.displayName}, ${it.age}" } ?: "PAIRLY Member",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        if (item.swipe.action == SwipeAction.SUPER_LIKE.wireValue) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Star,
                                                                contentDescription = "Super Liked you",
                                                                tint = SuperLikeVioletGold,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = sender?.let { "${it.city}, ${it.state}" } ?: "India",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (sender != null) {
                                                    IconButton(onClick = { onPassLike(sender) }) {
                                                        Icon(Icons.Filled.Close, contentDescription = "Pass")
                                                    }
                                                    Button(
                                                        onClick = { onLikeBack(sender) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = PairlyPink),
                                                        shape = RoundedCornerShape(50),
                                                        modifier = Modifier.testTag("like_back_button_${sender.userId}")
                                                    ) {
                                                        Text("Match")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    when (likesSentState) {
                        is UiState.Loading -> CenteredSpinner()
                        is UiState.Error -> CenteredError(likesSentState.message)
                        is UiState.Success -> {
                            val list = likesSentState.data
                            if (list.isEmpty()) {
                                EmptyConnectionsState(
                                    title = "No likes sent yet",
                                    subtitle = "Swipe right or tap Like on profiles in Discover to connect."
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    items(list, key = { it.swipe.toUid }) { item ->
                                        val recipient = item.recipient
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = recipient != null) {
                                                    if (recipient != null) inspectedProfile = recipient
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                PairlyPhotoImage(
                                                    photoSource = recipient?.photos?.firstOrNull(),
                                                    fallbackName = recipient?.displayName ?: "Member",
                                                    contentDescription = "Sent like to ${recipient?.displayName ?: "member"}",
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(CircleShape)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = recipient?.let { "${it.displayName}, ${it.age}" } ?: "PAIRLY Member",
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    Text(
                                                        text = recipient?.let { "${it.city}, ${it.state}" } ?: "India",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = if (item.swipe.action == SwipeAction.SUPER_LIKE.wireValue) {
                                                            "Super Liked"
                                                        } else {
                                                            "Liked"
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    unmatchTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unmatchTarget = null },
            title = { Text("Unmatch ${target.partner?.displayName ?: "user"}?") },
            text = {
                Text("This will end your match and close private messaging between you two.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUnmatch(target.match.docId)
                        unmatchTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Unmatch")
                }
            },
            dismissButton = {
                TextButton(onClick = { unmatchTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    inspectedProfile?.let { prof ->
        ProfileDetailSheet(
            profile = prof,
            onDismiss = { inspectedProfile = null },
            onBlock = {
                onBlockUser(prof.userId, null)
                inspectedProfile = null
            },
            onReport = {
                reportTarget = prof
                inspectedProfile = null
            }
        )
    }

    reportTarget?.let { target ->
        ReportUserDialog(
            targetName = target.displayName,
            onDismiss = { reportTarget = null },
            onSubmit = { reason, details ->
                onReportUser(target.userId, reason, details)
                reportTarget = null
            }
        )
    }
}

@Composable
private fun MatchCardRow(
    item: MatchWithProfile,
    onOpenChat: () -> Unit,
    onInspect: () -> Unit,
    onUnmatch: () -> Unit
) {
    val partner = item.partner
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenChat)
            .testTag("match_card_${item.match.docId}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PairlyPhotoImage(
                photoSource = partner?.photos?.firstOrNull(),
                fallbackName = partner?.displayName ?: "Match",
                contentDescription = "${partner?.displayName ?: "Match"} avatar",
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onInspect)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partner?.let { "${it.displayName}, ${it.age}" } ?: "New Match",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = partner?.let { "${it.city}, ${it.state}" } ?: "Mutual match",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onUnmatch,
                modifier = Modifier.testTag("unmatch_button_${item.match.docId}")
            ) {
                Icon(
                    imageVector = Icons.Filled.HeartBroken,
                    contentDescription = "Unmatch",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onOpenChat,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = PairlyPurple),
                modifier = Modifier.testTag("open_chat_button_${item.match.docId}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Chat")
            }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PairlyPink)
    }
}

@Composable
private fun CenteredError(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyConnectionsState(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = PairlyPink,
                modifier = Modifier.size(36.dp)
            )
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
