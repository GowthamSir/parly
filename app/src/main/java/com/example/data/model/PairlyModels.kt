package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Calendar

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System");

    companion object {
        fun fromName(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: DARK
    }
}

enum class DistanceTier(val wireValue: String, val label: String) {
    SAME_CITY("Same City", "Same City"),
    SAME_STATE("Same State", "Same State"),
    PAN_INDIA("Pan-India", "Anywhere in India");

    companion object {
        fun fromWire(raw: String?): DistanceTier =
            entries.firstOrNull { it.wireValue == raw } ?: PAN_INDIA
    }
}

enum class RelationshipPreference(val wireValue: String) {
    LONG_TERM("Long-term"),
    DATING("Dating"),
    FRIENDS("Friends"),
    EXPLORING("Exploring"),
    OPEN_TO_ALL("Open to all");

    companion object {
        fun fromWire(raw: String?): RelationshipPreference =
            entries.firstOrNull { it.wireValue == raw } ?: OPEN_TO_ALL
    }
}

enum class SwipeAction(val wireValue: String) {
    LIKE("like"),
    SUPER_LIKE("super_like"),
    PASS("pass");

    companion object {
        fun fromWire(raw: String?): SwipeAction =
            entries.firstOrNull { it.wireValue == raw } ?: PASS
    }
}

enum class ReportReason(val wireValue: String) {
    HARASSMENT("Harassment"),
    FAKE_PROFILE("Fake Profile"),
    INAPPROPRIATE_CONTENT("Inappropriate Content"),
    UNDERAGE_CONCERN("Underage Concern"),
    SPAM("Spam")
}

@Keep
@IgnoreExtraProperties
data class ProfilePrompt(
    val question: String = "",
    val answer: String = ""
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val age: Int = 18,
    val birthYear: Int = 2000,
    val city: String = "",
    val state: String = "",
    val bio: String = "",
    val relationshipPreference: String = RelationshipPreference.OPEN_TO_ALL.wireValue,
    val interests: List<String> = emptyList(),
    val hobbies: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val prompts: List<ProfilePrompt> = emptyList(),
    val photos: List<String> = emptyList(),
    val hidden: Boolean = false,
    val incognito: Boolean = false,
    val adultVerified: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PrivateAccount(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val email: String? = null,
    val dobIso: String = "",
    val minAgeFilter: Int = 18,
    val maxAgeFilter: Int = 45,
    val distanceTier: String = DistanceTier.PAN_INDIA.wireValue,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SwipeRecord(
    @DocumentId val docId: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val action: String = SwipeAction.PASS.wireValue,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MatchRecord(
    @DocumentId val docId: String = "",
    val memberUids: List<String> = emptyList(),
    val status: String = "active",
    val lastMessageText: String = "",
    val lastSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun otherMemberUid(currentUid: String): String =
        memberUids.firstOrNull { it != currentUid } ?: ""
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val docId: String = "",
    val matchId: String = "",
    val memberUids: List<String> = emptyList(),
    val senderId: String = "",
    val text: String = "",
    val imagePayload: String? = null,
    val moderationAcknowledged: Boolean = true,
    val readBy: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserBlock(
    @DocumentId val docId: String = "",
    val blockerUid: String = "",
    val blockedUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SafetyReport(
    @DocumentId val docId: String = "",
    val reporterUid: String = "",
    val reportedUid: String = "",
    val reason: String = ReportReason.HARASSMENT.wireValue,
    val details: String = "",
    val contextType: String = "profile",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class DiscoveryFilter(
    val minAge: Int = 18,
    val maxAge: Int = 50,
    val selectedState: String = "All India",
    val selectedCity: String = "All Cities",
    val relationshipPreference: String = "All",
    val selectedInterests: Set<String> = emptySet(),
    val distanceTier: DistanceTier = DistanceTier.PAN_INDIA
)

data class MatchWithProfile(
    val match: MatchRecord,
    val partner: UserProfile?,
    val isUnread: Boolean
)

data class LikeReceivedItem(
    val swipe: SwipeRecord,
    val sender: UserProfile?
)

data class LikeSentItem(
    val swipe: SwipeRecord,
    val recipient: UserProfile?
)

object AgeCalculator {
    /**
     * Parses YYYY-MM-DD and returns exact completed years of age, or null if invalid.
     */
    fun calculateAgeFromIso(dobIso: String): Int? {
        val parts = dobIso.trim().split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (year < 1920 || month !in 1..12 || day !in 1..31) return null

        val dobCal = Calendar.getInstance().apply {
            isLenient = false
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return try {
            // Validate calendar date (e.g., reject Feb 30)
            if (dobCal.get(Calendar.YEAR) != year ||
                dobCal.get(Calendar.MONTH) != month - 1 ||
                dobCal.get(Calendar.DAY_OF_MONTH) != day
            ) {
                return null
            }
            val today = Calendar.getInstance()
            if (dobCal.after(today)) return null
            var age = today.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
            val todayMonth = today.get(Calendar.MONTH)
            val dobMonth = dobCal.get(Calendar.MONTH)
            if (todayMonth < dobMonth ||
                (todayMonth == dobMonth && today.get(Calendar.DAY_OF_MONTH) < dobCal.get(Calendar.DAY_OF_MONTH))
            ) {
                age--
            }
            age
        } catch (_: Exception) {
            null
        }
    }

    fun formatDobIso(year: Int, monthOneBased: Int, day: Int): String =
        String.format("%04d-%02d-%02d", year, monthOneBased, day)
}
