package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.AgeCalculator
import com.example.data.model.ChatMessage
import com.example.data.model.DiscoveryFilter
import com.example.data.model.DistanceTier
import com.example.data.model.MatchRecord
import com.example.data.model.PrivateAccount
import com.example.data.model.ProfilePrompt
import com.example.data.model.ReportReason
import com.example.data.model.SwipeAction
import com.example.data.model.SwipeRecord
import com.example.data.model.UserBlock
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PairlyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing PAIRLY.")

    private fun unwrapAndLogFlowError(
        error: Throwable,
        uid: String,
        op: OperationType,
        path: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    fun observeMyProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> unwrapAndLogFlowError(e, uid, OperationType.GET, path) }
        )
    }

    fun observePrivateAccount(userId: String): Flow<PrivateAccount?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val path = "users/$uid/private/account"
        emitAll(
            db.collection("users").document(uid)
                .collection("private").document("account")
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(PrivateAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> unwrapAndLogFlowError(e, uid, OperationType.GET, path) }
        )
    }

    fun observeDiscoverableProfiles(userId: String): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val path = "users"
        emitAll(
            db.collection("users")
                .whereEqualTo("hidden", false)
                .whereEqualTo("incognito", false)
                .whereEqualTo("adultVerified", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> unwrapAndLogFlowError(e, uid, OperationType.LIST, path) }
        )
    }

    fun observeMySwipes(userId: String): Flow<List<SwipeRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val path = "swipes"
        emitAll(
            db.collection("swipes")
                .whereEqualTo("fromUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SwipeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> unwrapAndLogFlowError(e, uid, OperationType.LIST, path) }
        )
    }

    fun observeLikesReceived(userId: String): Flow<List<SwipeRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val path = "swipes"
        emitAll(
            db.collection("swipes")
                .whereEqualTo("toUid", uid)
                .whereIn("action", listOf(SwipeAction.LIKE.wireValue, SwipeAction.SUPER_LIKE.wireValue))
                .snapshots()
                .map { snap ->
                    snap.toObjects(SwipeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> unwrapAndLogFlowError(e, uid, OperationType.LIST, path) }
        )
    }

    fun observeMatches(userId: String): Flow<List<MatchRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val path = "matches"
        emitAll(
            db.collection("matches")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(MatchRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status == "active" }
                        .sortedByDescending {
                            it.lastMessageAt ?: it.createdAt ?: Timestamp(0, 0)
                        }
                }
                .catch { e -> unwrapAndLogFlowError(e, uid, OperationType.LIST, path) }
        )
    }

    fun observeBlocks(userId: String): Flow<List<UserBlock>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val path = "blocks"
        emitAll(
            db.collection("blocks")
                .whereEqualTo("blockerUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserBlock::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { e -> unwrapAndLogFlowError(e, uid, OperationType.LIST, path) }
        )
    }

    fun observeMessages(matchId: String, userId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch" }
        val path = "matches/$matchId/messages"
        emitAll(
            db.collection("matches").document(matchId)
                .collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp.now() }
                }
                .catch { e -> unwrapAndLogFlowError(e, uid, OperationType.LIST, path) }
        )
    }

    suspend fun getUserProfileById(targetUid: String): Result<UserProfile?> {
        val path = "users/$targetUid"
        return try {
            requireUserId()
            val snap = db.collection("users").document(targetUid).get().await()
            val profile = if (snap.exists()) {
                snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createOnboardingProfile(
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
        photos: List<String>
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val trimmedName = displayName.trim()
            require(trimmedName.length in 2..50) { "Display name must be 2 to 50 characters." }

            val calculatedAge = AgeCalculator.calculateAgeFromIso(dobIso)
                ?: throw IllegalArgumentException("Please select a valid date of birth.")
            require(calculatedAge >= 18) { "You must be at least 18 years old to join PAIRLY." }
            val birthYear = dobIso.substring(0, 4).toInt()

            require(city.trim().length in 2..60) { "Please select your city." }
            require(state.trim().length in 2..60) { "Please select your state." }

            val promptMaps = prompts
                .filter { it.question.isNotBlank() && it.answer.isNotBlank() }
                .take(5)
                .map { mapOf("question" to it.question.take(140), "answer" to it.answer.trim().take(300)) }

            val publicPayload = mapOf(
                "userId" to uid,
                "displayName" to trimmedName,
                "age" to calculatedAge,
                "birthYear" to birthYear,
                "city" to city.trim(),
                "state" to state.trim(),
                "bio" to bio.trim().take(500),
                "relationshipPreference" to relationshipPreference,
                "interests" to interests.take(12),
                "hobbies" to hobbies.take(12),
                "languages" to languages.take(10),
                "prompts" to promptMaps,
                "photos" to photos.take(6),
                "hidden" to false,
                "incognito" to false,
                "adultVerified" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val privatePayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "dobIso" to dobIso,
                "minAgeFilter" to 18,
                "maxAgeFilter" to 50,
                "distanceTier" to DistanceTier.PAN_INDIA.wireValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            auth.currentUser?.email?.takeIf { it.isNotBlank() && it.length <= 200 }?.let {
                privatePayload["email"] = it
            }

            val batch = db.batch()
            batch.set(db.collection("users").document(uid), publicPayload)
            batch.set(
                db.collection("users").document(uid).collection("private").document("account"),
                privatePayload
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        city: String,
        state: String,
        bio: String,
        relationshipPreference: String,
        interests: List<String>,
        hobbies: List<String>,
        languages: List<String>,
        prompts: List<ProfilePrompt>,
        photos: List<String>
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val trimmedName = displayName.trim()
            require(trimmedName.length in 2..50) { "Display name must be 2 to 50 characters." }
            require(city.trim().length in 2..60 && state.trim().length in 2..60) {
                "City and state are required."
            }

            val promptMaps = prompts
                .filter { it.question.isNotBlank() && it.answer.isNotBlank() }
                .take(5)
                .map { mapOf("question" to it.question.take(140), "answer" to it.answer.trim().take(300)) }

            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to trimmedName,
                    "city" to city.trim(),
                    "state" to state.trim(),
                    "bio" to bio.trim().take(500),
                    "relationshipPreference" to relationshipPreference,
                    "interests" to interests.take(12),
                    "hobbies" to hobbies.take(12),
                    "languages" to languages.take(10),
                    "prompts" to promptMaps,
                    "photos" to photos.take(6),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(hidden: Boolean, incognito: Boolean): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "hidden" to hidden,
                    "incognito" to incognito,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveFilterPreferences(filter: DiscoveryFilter): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/private/account"
            db.collection("users").document(uid)
                .collection("private").document("account")
                .update(
                    mapOf(
                        "minAgeFilter" to filter.minAge.coerceIn(18, 80),
                        "maxAgeFilter" to filter.maxAge.coerceIn(filter.minAge.coerceIn(18, 80), 100),
                        "distanceTier" to filter.distanceTier.wireValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Records a swipe and atomically creates a mutual match document if the target user
     * has already liked or super-liked the current user. Returns true when a new match occurs.
     */
    suspend fun recordSwipeAndMatchIfMutual(
        targetUid: String,
        action: SwipeAction
    ): Result<Boolean> {
        var path = "swipes"
        return try {
            val uid = requireUserId()
            require(targetUid.isNotBlank() && targetUid != uid) { "Invalid profile target." }

            val mySwipeId = "${uid}_$targetUid"
            val mySwipeRef = db.collection("swipes").document(mySwipeId)
            path = mySwipeRef.path

            val existingMySwipe = mySwipeRef.get().await()
            val isLikeAction = action == SwipeAction.LIKE || action == SwipeAction.SUPER_LIKE

            var isMutualMatch = false
            var matchRefToCreate: com.google.firebase.firestore.DocumentReference? = null
            var sortedMembers: List<String> = emptyList()

            if (isLikeAction) {
                val reverseSwipeSnap = db.collection("swipes")
                    .document("${targetUid}_$uid")
                    .get()
                    .await()
                val reverseAction = reverseSwipeSnap.getString("action")
                if (reverseAction == SwipeAction.LIKE.wireValue ||
                    reverseAction == SwipeAction.SUPER_LIKE.wireValue
                ) {
                    sortedMembers = listOf(uid, targetUid).sorted()
                    val matchId = "${sortedMembers[0]}_${sortedMembers[1]}"
                    val candidateMatchRef = db.collection("matches").document(matchId)
                    val existingMatch = candidateMatchRef.get().await()
                    if (!existingMatch.exists()) {
                        isMutualMatch = true
                        matchRefToCreate = candidateMatchRef
                    }
                }
            }

            val batch = db.batch()
            if (existingMySwipe.exists()) {
                batch.update(
                    mySwipeRef,
                    mapOf(
                        "action" to action.wireValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    mySwipeRef,
                    mapOf(
                        "fromUid" to uid,
                        "toUid" to targetUid,
                        "action" to action.wireValue,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (isMutualMatch && matchRefToCreate != null) {
                batch.set(
                    matchRefToCreate,
                    mapOf(
                        "memberUids" to sortedMembers,
                        "status" to "active",
                        "lastMessageText" to "",
                        "lastSenderId" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(isMutualMatch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        matchId: String,
        memberUids: List<String>,
        text: String,
        imagePayload: String?,
        moderationAcknowledged: Boolean
    ): Result<Unit> {
        var path = "matches/$matchId/messages"
        return try {
            val uid = requireUserId()
            require(memberUids.size == 2 && uid in memberUids) { "Not a member of this match." }
            val trimmedText = text.trim()
            require(trimmedText.isNotEmpty() || !imagePayload.isNullOrBlank()) {
                "Message cannot be empty."
            }
            require(trimmedText.length <= 1000) { "Message exceeds 1000 characters." }

            val matchRef = db.collection("matches").document(matchId)
            val msgRef = matchRef.collection("messages").document()
            path = msgRef.path

            val msgPayload = mutableMapOf<String, Any>(
                "matchId" to matchId,
                "memberUids" to memberUids,
                "senderId" to uid,
                "text" to trimmedText,
                "moderationAcknowledged" to moderationAcknowledged,
                "readBy" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!imagePayload.isNullOrBlank()) {
                msgPayload["imagePayload"] = imagePayload
            }

            val previewText = when {
                trimmedText.isNotBlank() -> trimmedText.take(120)
                !imagePayload.isNullOrBlank() -> "Shared a photo"
                else -> ""
            }

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                matchRef,
                mapOf(
                    "lastMessageText" to previewText,
                    "lastSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markMessagesAsRead(matchId: String, unreadMessages: List<ChatMessage>): Result<Unit> {
        if (unreadMessages.isEmpty()) return Result.success(Unit)
        val path = "matches/$matchId/messages"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            unreadMessages.take(25).forEach { msg ->
                if (msg.docId.isNotBlank() && uid !in msg.readBy) {
                    val ref = db.collection("matches").document(matchId)
                        .collection("messages").document(msg.docId)
                    batch.update(
                        ref,
                        mapOf(
                            "readBy" to (msg.readBy + uid).distinct().take(2),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun unmatch(matchId: String): Result<Unit> {
        val path = "matches/$matchId"
        return try {
            requireUserId()
            db.collection("matches").document(matchId).update(
                mapOf(
                    "status" to "unmatched",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(matchId: String): Result<Unit> {
        val path = "matches/$matchId/messages"
        return try {
            val uid = requireUserId()
            val messagesSnap = db.collection("matches").document(matchId)
                .collection("messages")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()

            val matchRef = db.collection("matches").document(matchId)
            val batch = db.batch()
            messagesSnap.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.update(
                matchRef,
                mapOf(
                    "lastMessageText" to "",
                    "lastSenderId" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun blockUser(blockedUid: String, activeMatchId: String? = null): Result<Unit> {
        var path = "blocks"
        return try {
            val uid = requireUserId()
            require(blockedUid.isNotBlank() && blockedUid != uid) { "Invalid block target." }
            val blockId = "${uid}_$blockedUid"
            val blockRef = db.collection("blocks").document(blockId)
            path = blockRef.path

            val batch = db.batch()
            batch.set(
                blockRef,
                mapOf(
                    "blockerUid" to uid,
                    "blockedUid" to blockedUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            if (!activeMatchId.isNullOrBlank()) {
                val matchRef = db.collection("matches").document(activeMatchId)
                batch.update(
                    matchRef,
                    mapOf(
                        "status" to "unmatched",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(blockedUid: String): Result<Unit> {
        var path = "blocks"
        return try {
            val uid = requireUserId()
            val blockRef = db.collection("blocks").document("${uid}_$blockedUid")
            path = blockRef.path
            blockRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        reportedUid: String,
        reason: ReportReason,
        details: String,
        contextType: String
    ): Result<Unit> {
        var path = "reports"
        return try {
            val uid = requireUserId()
            require(reportedUid.isNotBlank() && reportedUid != uid) { "Invalid report target." }
            val reportRef = db.collection("reports").document()
            path = reportRef.path
            reportRef.set(
                mapOf(
                    "reporterUid" to uid,
                    "reportedUid" to reportedUid,
                    "reason" to reason.wireValue,
                    "details" to details.trim().take(500),
                    "contextType" to contextType,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        var path = "users"
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("No authenticated session found.")
            val uid = user.uid
            path = "users/$uid"

            val mySwipes = db.collection("swipes").whereEqualTo("fromUid", uid).get().await()
            val myBlocks = db.collection("blocks").whereEqualTo("blockerUid", uid).get().await()
            val myMatches = db.collection("matches").whereArrayContains("memberUids", uid).get().await()

            val batch = db.batch()
            mySwipes.documents.forEach { batch.delete(it.reference) }
            myBlocks.documents.forEach { batch.delete(it.reference) }
            myMatches.documents.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "status" to "unmatched",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.delete(db.collection("users").document(uid).collection("private").document("account"))
            batch.delete(db.collection("users").document(uid))
            batch.commit().await()

            try {
                user.delete().await()
            } catch (e: Exception) {
                Log.w("PairlyRepo", "Profile deleted; Firebase Auth delete requires recent login: ${e.message}")
                auth.signOut()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
