package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PairlyRepository
import com.example.data.model.ChatMessage
import com.example.data.model.ReportReason
import com.example.data.toReadableFirestoreMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: PairlyRepository,
    val currentUserId: String,
    val matchId: String
) : ViewModel() {

    private companion object {
        const val TAG = "ChatVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }

    val messagesState: StateFlow<UiState<List<ChatMessage>>> =
        repository.observeMessages(matchId, currentUserId)
            .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing chat messages", rootError)
                emit(UiState.Error(rootError.toReadableFirestoreMessage("Failed to load conversation")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun markIncomingAsRead(messages: List<ChatMessage>) {
        val unread = messages.filter { it.senderId != currentUserId && currentUserId !in it.readBy }
        if (unread.isEmpty()) return
        viewModelScope.launch {
            repository.markMessagesAsRead(matchId, unread)
        }
    }

    fun sendTextMessage(memberUids: List<String>, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isSending.value = true
            val result = repository.sendMessage(
                matchId = matchId,
                memberUids = memberUids,
                text = text,
                imagePayload = null,
                moderationAcknowledged = true
            )
            _isSending.value = false
            result.onFailure { err ->
                _feedbackMessage.value = err.toReadableFirestoreMessage("Could not send message.")
            }
        }
    }

    fun sendModeratedImageMessage(
        memberUids: List<String>,
        caption: String,
        imageDataUri: String
    ) {
        viewModelScope.launch {
            _isSending.value = true
            val result = repository.sendMessage(
                matchId = matchId,
                memberUids = memberUids,
                text = caption,
                imagePayload = imageDataUri,
                moderationAcknowledged = true
            )
            _isSending.value = false
            result.onFailure { err ->
                _feedbackMessage.value = err.toReadableFirestoreMessage("Could not send image.")
            }
        }
    }

    fun deleteConversation(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteConversation(matchId)
                .onSuccess {
                    _feedbackMessage.value = "Conversation cleared."
                    onComplete()
                }
                .onFailure { err ->
                    _feedbackMessage.value = err.toReadableFirestoreMessage("Could not delete conversation.")
                }
        }
    }

    fun blockPartner(partnerUid: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.blockUser(partnerUid, matchId)
                .onSuccess {
                    _feedbackMessage.value = "User blocked."
                    onComplete()
                }
                .onFailure { err ->
                    _feedbackMessage.value = err.toReadableFirestoreMessage("Could not block user.")
                }
        }
    }

    fun reportPartner(
        partnerUid: String,
        reason: ReportReason,
        details: String
    ) {
        viewModelScope.launch {
            repository.submitReport(partnerUid, reason, details, "chat")
                .onSuccess { _feedbackMessage.value = "Report submitted." }
                .onFailure { err ->
                    _feedbackMessage.value = err.toReadableFirestoreMessage("Could not submit report.")
                }
        }
    }
}
