package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PairlyRepository
import com.example.data.model.DiscoveryFilter
import com.example.data.model.DistanceTier
import com.example.data.model.LikeReceivedItem
import com.example.data.model.LikeSentItem
import com.example.data.model.MatchRecord
import com.example.data.model.MatchWithProfile
import com.example.data.model.PrivateAccount
import com.example.data.model.ProfilePrompt
import com.example.data.model.ReportReason
import com.example.data.model.SwipeAction
import com.example.data.model.SwipeRecord
import com.example.data.model.UserBlock
import com.example.data.model.UserProfile
import com.example.data.toReadableFirestoreMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class MainSessionViewModel(
    private val repository: PairlyRepository,
    val currentUserId: String
) : ViewModel() {

    private companion object {
        const val TAG = "MainSessionVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.toSafeUiState(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.toReadableFirestoreMessage("Failed to load $label")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val myProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeMyProfile(currentUserId).toSafeUiState("profile")

    val privateAccountState: StateFlow<UiState<PrivateAccount?>> =
        repository.observePrivateAccount(currentUserId).toSafeUiState("account")

    private val discoverableProfilesState: StateFlow<UiState<List<UserProfile>>> =
        repository.observeDiscoverableProfiles(currentUserId).toSafeUiState("discovery profiles")

    private val mySwipesState: StateFlow<UiState<List<SwipeRecord>>> =
        repository.observeMySwipes(currentUserId).toSafeUiState("swipes")

    private val likesReceivedRawState: StateFlow<UiState<List<SwipeRecord>>> =
        repository.observeLikesReceived(currentUserId).toSafeUiState("likes received")

    private val matchesRawState: StateFlow<UiState<List<MatchRecord>>> =
        repository.observeMatches(currentUserId).toSafeUiState("matches")

    val blocksState: StateFlow<UiState<List<UserBlock>>> =
        repository.observeBlocks(currentUserId).toSafeUiState("blocked users")

    private val _discoveryFilter = MutableStateFlow(DiscoveryFilter())
    val discoveryFilter: StateFlow<DiscoveryFilter> = _discoveryFilter.asStateFlow()

    private val _partnerCache = MutableStateFlow<Map<String, UserProfile>>(emptyMap())

    private val _newMatchCelebration = MutableStateFlow<UserProfile?>(null)
    val newMatchCelebration: StateFlow<UserProfile?> = _newMatchCelebration.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // Sync saved filter defaults from PrivateAccount once loaded
    init {
        viewModelScope.launch {
            privateAccountState.collect { state ->
                if (state is UiState.Success && state.data != null) {
                    val acct = state.data
                    _discoveryFilter.value = _discoveryFilter.value.copy(
                        minAge = acct.minAgeFilter.coerceIn(18, 80),
                        maxAge = acct.maxAgeFilter.coerceIn(18, 100),
                        distanceTier = DistanceTier.fromWire(acct.distanceTier)
                    )
                }
            }
        }
    }

    val discoveryDeckState: StateFlow<UiState<List<UserProfile>>> = combine(
        myProfileState,
        discoverableProfilesState,
        mySwipesState,
        blocksState,
        _discoveryFilter
    ) { myProfState, candidatesState, swipesState, blocksUiState, filter ->
        when {
            candidatesState is UiState.Error -> UiState.Error(candidatesState.message)
            candidatesState is UiState.Loading || myProfState is UiState.Loading -> UiState.Loading
            else -> {
                val me = (myProfState as? UiState.Success)?.data
                val candidates = (candidatesState as? UiState.Success)?.data.orEmpty()
                val swipedIds = (swipesState as? UiState.Success)?.data?.map { it.toUid }?.toSet().orEmpty()
                val blockedIds = (blocksUiState as? UiState.Success)?.data?.map { it.blockedUid }?.toSet().orEmpty()

                val filtered = candidates.filter { candidate ->
                    val notSelf = candidate.userId != currentUserId
                    val notSwiped = candidate.userId !in swipedIds
                    val notBlocked = candidate.userId !in blockedIds
                    val ageMatch = candidate.age in filter.minAge..filter.maxAge
                    val prefMatch = filter.relationshipPreference == "All" ||
                        candidate.relationshipPreference == filter.relationshipPreference
                    val stateFilterMatch = filter.selectedState == "All India" ||
                        candidate.state.equals(filter.selectedState, ignoreCase = true)
                    val cityFilterMatch = filter.selectedCity == "All Cities" ||
                        candidate.city.equals(filter.selectedCity, ignoreCase = true)
                    val distanceMatch = when (filter.distanceTier) {
                        DistanceTier.SAME_CITY ->
                            me == null || candidate.city.equals(me.city, ignoreCase = true)
                        DistanceTier.SAME_STATE ->
                            me == null || candidate.state.equals(me.state, ignoreCase = true)
                        DistanceTier.PAN_INDIA -> true
                    }
                    val interestMatch = filter.selectedInterests.isEmpty() ||
                        candidate.interests.any { it in filter.selectedInterests } ||
                        candidate.hobbies.any { it in filter.selectedInterests }

                    notSelf && notSwiped && notBlocked && ageMatch && prefMatch &&
                        stateFilterMatch && cityFilterMatch && distanceMatch && interestMatch
                }
                UiState.Success(filtered)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val matchesWithProfilesState: StateFlow<UiState<List<MatchWithProfile>>> = combine(
        matchesRawState,
        discoverableProfilesState,
        blocksState,
        _partnerCache
    ) { matchesUi, discoverableUi, blocksUi, cache ->
        when (matchesUi) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(matchesUi.message)
            is UiState.Success -> {
                val blockedIds = (blocksUi as? UiState.Success)?.data?.map { it.blockedUid }?.toSet().orEmpty()
                val discoverMap = (discoverableUi as? UiState.Success)?.data?.associateBy { it.userId }.orEmpty()
                val items = matchesUi.data
                    .filter { match -> match.otherMemberUid(currentUserId) !in blockedIds }
                    .map { match ->
                        val partnerUid = match.otherMemberUid(currentUserId)
                        val partner = discoverMap[partnerUid] ?: cache[partnerUid]
                        if (partner == null && partnerUid.isNotBlank()) {
                            fetchMissingPartner(partnerUid)
                        }
                        val unread = match.lastSenderId.isNotBlank() &&
                            match.lastSenderId != currentUserId &&
                            match.lastMessageText.isNotBlank()
                        MatchWithProfile(match = match, partner = partner, isUnread = unread)
                    }
                UiState.Success(items)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val likesReceivedState: StateFlow<UiState<List<LikeReceivedItem>>> = combine(
        likesReceivedRawState,
        mySwipesState,
        discoverableProfilesState,
        blocksState,
        _partnerCache
    ) { likesUi, mySwipesUi, discoverableUi, blocksUi, cache ->
        when (likesUi) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(likesUi.message)
            is UiState.Success -> {
                val blockedIds = (blocksUi as? UiState.Success)?.data?.map { it.blockedUid }?.toSet().orEmpty()
                val myLikedOrPassedIds = (mySwipesUi as? UiState.Success)?.data
                    ?.filter { it.action != SwipeAction.PASS.wireValue }
                    ?.map { it.toUid }
                    ?.toSet()
                    .orEmpty()
                val discoverMap = (discoverableUi as? UiState.Success)?.data?.associateBy { it.userId }.orEmpty()

                val items = likesUi.data
                    .filter { it.fromUid !in blockedIds && it.fromUid !in myLikedOrPassedIds }
                    .map { swipe ->
                        val sender = discoverMap[swipe.fromUid] ?: cache[swipe.fromUid]
                        if (sender == null) {
                            fetchMissingPartner(swipe.fromUid)
                        }
                        LikeReceivedItem(swipe = swipe, sender = sender)
                    }
                UiState.Success(items)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val likesSentState: StateFlow<UiState<List<LikeSentItem>>> = combine(
        mySwipesState,
        discoverableProfilesState,
        blocksState,
        _partnerCache
    ) { swipesUi, discoverableUi, blocksUi, cache ->
        when (swipesUi) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(swipesUi.message)
            is UiState.Success -> {
                val blockedIds = (blocksUi as? UiState.Success)?.data?.map { it.blockedUid }?.toSet().orEmpty()
                val discoverMap = (discoverableUi as? UiState.Success)?.data?.associateBy { it.userId }.orEmpty()

                val items = swipesUi.data
                    .filter {
                        (it.action == SwipeAction.LIKE.wireValue || it.action == SwipeAction.SUPER_LIKE.wireValue) &&
                            it.toUid !in blockedIds
                    }
                    .map { swipe ->
                        val recipient = discoverMap[swipe.toUid] ?: cache[swipe.toUid]
                        if (recipient == null) {
                            fetchMissingPartner(swipe.toUid)
                        }
                        LikeSentItem(swipe = swipe, recipient = recipient)
                    }
                UiState.Success(items)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val blockedUsersProfilesState: StateFlow<List<Pair<UserBlock, UserProfile?>>> = combine(
        blocksState,
        _partnerCache
    ) { blocksUi, cache ->
        val blocks = (blocksUi as? UiState.Success)?.data.orEmpty()
        blocks.map { block ->
            val profile = cache[block.blockedUid]
            if (profile == null) fetchMissingPartner(block.blockedUid)
            block to profile
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val requestedPartnerFetches = mutableSetOf<String>()

    private fun fetchMissingPartner(uid: String) {
        if (uid.isBlank() || uid in requestedPartnerFetches) return
        requestedPartnerFetches.add(uid)
        viewModelScope.launch {
            repository.getUserProfileById(uid).onSuccess { prof ->
                if (prof != null) {
                    _partnerCache.value = _partnerCache.value + (uid to prof)
                }
            }
        }
    }

    fun getCachedProfile(uid: String): UserProfile? = _partnerCache.value[uid]

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun dismissMatchCelebration() {
        _newMatchCelebration.value = null
    }

    fun updateFilter(newFilter: DiscoveryFilter) {
        _discoveryFilter.value = newFilter
        viewModelScope.launch {
            repository.saveFilterPreferences(newFilter)
        }
    }

    fun swipeOnProfile(profile: UserProfile, action: SwipeAction) {
        viewModelScope.launch {
            val result = repository.recordSwipeAndMatchIfMutual(profile.userId, action)
            result.onSuccess { matched ->
                if (matched) {
                    _partnerCache.value = _partnerCache.value + (profile.userId to profile)
                    _newMatchCelebration.value = profile
                }
            }.onFailure { err ->
                _actionMessage.value = err.toReadableFirestoreMessage("Unable to record swipe.")
            }
        }
    }

    fun completeOnboarding(
        displayName: String,
        dobIso: String,
        city: String,
        state: String,
        bio: String,
        relationshipPreference: String,
        interests: List<String>,
        hobbies: List<String>,
        languages: List<String>,
        prompts: List<ProfilePrompt>,
        photos: List<String>,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createOnboardingProfile(
                displayName = displayName,
                dobIso = dobIso,
                city = city,
                state = state,
                bio = bio,
                relationshipPreference = relationshipPreference,
                interests = interests,
                hobbies = hobbies,
                languages = languages,
                prompts = prompts,
                photos = photos
            )
            _isBusy.value = false
            result.onSuccess {
                _actionMessage.value = "Welcome to PAIRLY!"
                onSuccess()
            }.onFailure { err ->
                _actionMessage.value = err.toReadableFirestoreMessage("Could not save profile.")
            }
        }
    }

    fun updateProfile(
        displayName: String,
        city: String,
        state: String,
        bio: String,
        relationshipPreference: String,
        interests: List<String>,
        hobbies: List<String>,
        languages: List<String>,
        prompts: List<ProfilePrompt>,
        photos: List<String>,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateProfile(
                displayName = displayName,
                city = city,
                state = state,
                bio = bio,
                relationshipPreference = relationshipPreference,
                interests = interests,
                hobbies = hobbies,
                languages = languages,
                prompts = prompts,
                photos = photos
            )
            _isBusy.value = false
            result.onSuccess {
                _actionMessage.value = "Profile updated."
                onSuccess()
            }.onFailure { err ->
                _actionMessage.value = err.toReadableFirestoreMessage("Failed to update profile.")
            }
        }
    }

    fun updatePrivacySettings(hidden: Boolean, incognito: Boolean) {
        viewModelScope.launch {
            repository.updatePrivacySettings(hidden, incognito)
                .onFailure { err ->
                    _actionMessage.value = err.toReadableFirestoreMessage("Unable to update privacy.")
                }
        }
    }

    fun unmatch(matchId: String) {
        viewModelScope.launch {
            repository.unmatch(matchId)
                .onSuccess { _actionMessage.value = "Unmatched." }
                .onFailure { err ->
                    _actionMessage.value = err.toReadableFirestoreMessage("Unable to unmatch.")
                }
        }
    }

    fun blockUser(targetUid: String, activeMatchId: String? = null) {
        viewModelScope.launch {
            repository.blockUser(targetUid, activeMatchId)
                .onSuccess { _actionMessage.value = "User blocked and hidden from your feed." }
                .onFailure { err ->
                    _actionMessage.value = err.toReadableFirestoreMessage("Unable to block user.")
                }
        }
    }

    fun unblockUser(targetUid: String) {
        viewModelScope.launch {
            repository.unblockUser(targetUid)
                .onSuccess { _actionMessage.value = "User unblocked." }
                .onFailure { err ->
                    _actionMessage.value = err.toReadableFirestoreMessage("Unable to unblock user.")
                }
        }
    }

    fun submitReport(
        reportedUid: String,
        reason: ReportReason,
        details: String,
        contextType: String
    ) {
        viewModelScope.launch {
            repository.submitReport(reportedUid, reason, details, contextType)
                .onSuccess { _actionMessage.value = "Report submitted. Thank you for keeping PAIRLY safe." }
                .onFailure { err ->
                    _actionMessage.value = err.toReadableFirestoreMessage("Unable to submit report.")
                }
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteAccount()
            _isBusy.value = false
            result.onSuccess {
                onComplete()
            }.onFailure { err ->
                _actionMessage.value = err.toReadableFirestoreMessage("Could not delete account.")
            }
        }
    }
}
