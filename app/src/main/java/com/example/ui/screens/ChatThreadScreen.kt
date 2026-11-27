package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ImageEncoder
import com.example.data.model.ChatMessage
import com.example.data.model.MatchRecord
import com.example.data.model.UserProfile
import com.example.ui.components.PairlyPhotoImage
import com.example.ui.components.ProfileDetailSheet
import com.example.ui.components.ReportUserDialog
import com.example.ui.theme.PairlyPink
import com.example.ui.theme.PairlyPurple
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    chatViewModel: ChatViewModel,
    match: MatchRecord?,
    partner: UserProfile?,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val messagesState by chatViewModel.messagesState.collectAsStateWithLifecycle()
    val isSending by chatViewModel.isSending.collectAsStateWithLifecycle()
    val feedback by chatViewModel.feedbackMessage.collectAsStateWithLifecycle()

    var draftText by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingModeratedImage by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            ImageEncoder.encodeUriToDataUri(context, uri)
                .onSuccess { dataUri -> pendingModeratedImage = dataUri }
                .onFailure { err ->
                    snackbarHostState.showSnackbar(err.message ?: "Could not load image.")
                }
        }
    }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            chatViewModel.clearFeedback()
        }
    }

    LaunchedEffect(messagesState) {
        val msgs = (messagesState as? UiState.Success)?.data ?: return@LaunchedEffect
        chatViewModel.markIncomingAsRead(msgs)
        if (msgs.isNotEmpty()) {
            listState.animateScrollToItem(msgs.lastIndex)
        }
    }

    val partnerUid = match?.otherMemberUid(chatViewModel.currentUserId) ?: partner?.userId.orEmpty()
    val partnerName = partner?.displayName ?: "Match"

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to messages"
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.clickable(enabled = partner != null) {
                            showProfileSheet = true
                        }
                    ) {
                        PairlyPhotoImage(
                            photoSource = partner?.photos?.firstOrNull(),
                            fallbackName = partnerName,
                            contentDescription = "$partnerName photo",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        )
                        Column {
                            Text(
                                text = partnerName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            partner?.let {
                                Text(
                                    text = "${it.city}, ${it.state}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("chat_safety_menu_button")
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Chat safety options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Report $partnerName") },
                                leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showReportDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block $partnerName") },
                                leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showBlockDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Conversation") },
                                leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                when (val state = messagesState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PairlyPink)
                        }
                    }

                    is UiState.Error -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    is UiState.Success -> {
                        val messages = state.data
                        if (messages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Say hello to $partnerName! Remember to keep conversations respectful and safe.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .widthIn(max = 640.dp)
                                    .fillMaxSize()
                            ) {
                                items(messages, key = { it.docId }) { msg ->
                                    ChatMessageBubble(
                                        message = msg,
                                        isMine = msg.senderId == chatViewModel.currentUserId,
                                        partnerUid = partnerUid,
                                        partnerName = partnerName
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Composer Bar
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isSending && match != null,
                        modifier = Modifier.testTag("chat_attach_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = "Share moderated photo",
                            tint = PairlyPink
                        )
                    }

                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { if (it.length <= 1000) draftText = it },
                        placeholder = { Text("Message $partnerName...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )

                    Surface(
                        onClick = {
                            if (match != null && draftText.isNotBlank() && !isSending) {
                                val textToSend = draftText
                                draftText = ""
                                chatViewModel.sendTextMessage(match.memberUids, textToSend)
                            }
                        },
                        enabled = match != null && draftText.isNotBlank() && !isSending,
                        shape = CircleShape,
                        color = if (draftText.isNotBlank()) PairlyPurple else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = if (draftText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Image Moderation Safety Confirmation Dialog
    pendingModeratedImage?.let { dataUri ->
        AlertDialog(
            onDismissRequest = { pendingModeratedImage = null },
            title = { Text("Photo Safety & Moderation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PairlyPhotoImage(
                        photoSource = dataUri,
                        fallbackName = partnerName,
                        contentDescription = "Selected photo preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Text(
                        text = "Confirm this photo follows PAIRLY guidelines: no non-consensual, abusive, or explicit content. Recipients can report shared images at any time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (match != null) {
                            chatViewModel.sendModeratedImageMessage(
                                memberUids = match.memberUids,
                                caption = draftText.trim(),
                                imageDataUri = dataUri
                            )
                            draftText = ""
                        }
                        pendingModeratedImage = null
                    },
                    modifier = Modifier.testTag("confirm_send_moderated_photo")
                ) {
                    Text("Send Photo")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingModeratedImage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showProfileSheet && partner != null) {
        ProfileDetailSheet(
            profile = partner,
            onDismiss = { showProfileSheet = false },
            onBlock = {
                showProfileSheet = false
                showBlockDialog = true
            },
            onReport = {
                showProfileSheet = false
                showReportDialog = true
            }
        )
    }

    if (showReportDialog && partnerUid.isNotBlank()) {
        ReportUserDialog(
            targetName = partnerName,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                chatViewModel.reportPartner(partnerUid, reason, details)
                showReportDialog = false
            }
        )
    }

    if (showBlockDialog && partnerUid.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Block $partnerName?") },
            text = {
                Text("Blocking ends your match and removes $partnerName from your discovery and chat lists.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockDialog = false
                        chatViewModel.blockPartner(partnerUid, onComplete = onBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Conversation?") },
            text = {
                Text("This permanently clears all messages in this chat thread.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        chatViewModel.deleteConversation()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    partnerUid: String,
    partnerName: String
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timestampText = message.createdAt?.toDate()?.let { timeFormat.format(it) }.orEmpty()
    val isReadByPartner = partnerUid.isNotBlank() && partnerUid in message.readBy

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        val bubbleShape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (isMine) 18.dp else 4.dp,
            bottomEnd = if (isMine) 4.dp else 18.dp
        )

        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(bubbleShape)
                .then(
                    if (isMine) {
                        Modifier.background(Brush.linearGradient(listOf(PairlyPurple, Color(0xFF7E4CE0))))
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    }
                )
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!message.imagePayload.isNullOrBlank()) {
                    PairlyPhotoImage(
                        photoSource = message.imagePayload,
                        fallbackName = partnerName,
                        contentDescription = "Shared chat photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(3.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (timestampText.isNotBlank()) {
                Text(
                    text = timestampText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isMine) {
                Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = if (isReadByPartner) "Seen" else "Sent",
                    tint = if (isReadByPartner) PairlyPink else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (isReadByPartner) "Seen" else "Sent",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isReadByPartner) PairlyPink else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
