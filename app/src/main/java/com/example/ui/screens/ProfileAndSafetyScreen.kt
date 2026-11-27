package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ImageEncoder
import com.example.data.model.PairlyCatalog
import com.example.data.model.PrivateAccount
import com.example.data.model.ProfilePrompt
import com.example.data.model.RelationshipPreference
import com.example.data.model.ThemeMode
import com.example.data.model.UserBlock
import com.example.data.model.UserProfile
import com.example.ui.components.PairlyPhotoImage
import com.example.ui.components.SelectableTagGroup
import com.example.ui.theme.PairlyPink
import com.example.ui.theme.PairlyPurple
import kotlinx.coroutines.launch

@Composable
fun ProfileAndSafetyScreen(
    profile: UserProfile?,
    privateAccount: PrivateAccount?,
    blockedUsers: List<Pair<UserBlock, UserProfile?>>,
    themeMode: ThemeMode,
    isBusy: Boolean,
    onChangeThemeMode: (ThemeMode) -> Unit,
    onUpdatePrivacy: (hidden: Boolean, incognito: Boolean) -> Unit,
    onSaveProfileEdits: (
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
        onDone: () -> Unit
    ) -> Unit,
    onUnblockUser: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    var showEditSheet by remember { mutableStateOf(false) }
    var showBlockedSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Profile & Safety",
                style = MaterialTheme.typography.headlineLarge
            )

            // Profile Hero Summary Card
            if (profile != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            PairlyPhotoImage(
                                photoSource = profile.photos.firstOrNull(),
                                fallbackName = profile.displayName,
                                contentDescription = "Your profile photo",
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${profile.displayName}, ${profile.age}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${profile.city}, ${profile.state}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${profile.photos.size}/6 photos · ${profile.relationshipPreference}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PairlyPink
                                )
                            }
                        }

                        if (profile.bio.isNotBlank()) {
                            Text(
                                text = profile.bio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { showEditSheet = true },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = PairlyPurple),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("edit_profile_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Edit Profile & Photos")
                        }
                    }
                }
            }

            // Privacy & Visibility Controls
            if (profile != null) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = PairlyPink)
                            Text("Privacy & Visibility", style = MaterialTheme.typography.titleLarge)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Hide Profile", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Pause your profile in discovery while keeping existing matches.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = profile.hidden,
                                onCheckedChange = { checked ->
                                    onUpdatePrivacy(checked, profile.incognito)
                                },
                                modifier = Modifier.testTag("hide_profile_switch")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Incognito Mode", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Stay invisible in discovery; only people you like can see you.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = profile.incognito,
                                onCheckedChange = { checked ->
                                    onUpdatePrivacy(profile.hidden, checked)
                                },
                                modifier = Modifier.testTag("incognito_switch")
                            )
                        }
                    }
                }
            }

            // Theme Appearance Selector
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = PairlyPink)
                        Text("Appearance", style = MaterialTheme.typography.titleLarge)
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { onChangeThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = ThemeMode.entries.size
                                ),
                                modifier = Modifier.testTag("theme_mode_${mode.name}")
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }

            // Safety & Account Management
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Account & Safety", style = MaterialTheme.typography.titleLarge)
                    privateAccount?.email?.let { email ->
                        Text(
                            text = "Private account email: $email (never shown publicly)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = { showBlockedSheet = true },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("manage_blocked_users_button")
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Blocked Users (${blockedUsers.size})")
                    }

                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("logout_button")
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Log Out")
                    }

                    TextButton(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("delete_account_button")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Account Permanently")
                    }
                }
            }
        }
    }

    if (showEditSheet && profile != null) {
        EditProfileSheet(
            profile = profile,
            isBusy = isBusy,
            onDismiss = { showEditSheet = false },
            onSave = { name, city, state, bio, pref, interests, hobbies, langs, prompts, photos ->
                onSaveProfileEdits(
                    name, city, state, bio, pref, interests, hobbies, langs, prompts, photos
                ) {
                    showEditSheet = false
                }
            }
        )
    }

    if (showBlockedSheet) {
        BlockedUsersSheet(
            blockedUsers = blockedUsers,
            onUnblock = onUnblockUser,
            onDismiss = { showBlockedSheet = false }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out of PAIRLY?") },
            text = { Text("You can sign back in anytime with your Google account.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onSignOut()
                    }
                ) { Text("Log Out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account Permanently?") },
            text = {
                Text("This removes your PAIRLY profile, swipes, matches, and private account record. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) { Text("Delete Account") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    profile: UserProfile,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(profile.displayName) }
    var selectedState by remember { mutableStateOf(profile.state) }
    var stateExpanded by remember { mutableStateOf(false) }
    var selectedCity by remember { mutableStateOf(profile.city) }
    var cityExpanded by remember { mutableStateOf(false) }
    var bio by remember { mutableStateOf(profile.bio) }
    var relationshipPref by remember { mutableStateOf(profile.relationshipPreference) }
    var interests by remember { mutableStateOf(profile.interests.toSet()) }
    var hobbies by remember { mutableStateOf(profile.hobbies.toSet()) }
    var languages by remember { mutableStateOf(profile.languages.toSet()) }

    val existingPrompt = profile.prompts.firstOrNull()
    var promptQuestion by remember {
        mutableStateOf(existingPrompt?.question ?: PairlyCatalog.promptQuestions.first())
    }
    var promptExpanded by remember { mutableStateOf(false) }
    var promptAnswer by remember { mutableStateOf(existingPrompt?.answer.orEmpty()) }
    var photos by remember { mutableStateOf(profile.photos) }
    var photoBusy by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            photoBusy = true
            val next = photos.toMutableList()
            for (uri in uris) {
                if (next.size >= 6) break
                ImageEncoder.encodeUriToDataUri(context, uri)
                    .onSuccess { next.add(it) }
            }
            photos = next
            photoBusy = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Edit Profile & Photos", style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 50) displayName = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = stateExpanded,
                onExpandedChange = { stateExpanded = !stateExpanded }
            ) {
                OutlinedTextField(
                    value = selectedState,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("State") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stateExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = stateExpanded,
                    onDismissRequest = { stateExpanded = false }
                ) {
                    PairlyCatalog.allStates.forEach { st ->
                        DropdownMenuItem(
                            text = { Text(st) },
                            onClick = {
                                selectedState = st
                                selectedCity = PairlyCatalog.citiesForState(st).firstOrNull().orEmpty()
                                stateExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = cityExpanded,
                onExpandedChange = { cityExpanded = !cityExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCity,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("City") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cityExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = cityExpanded,
                    onDismissRequest = { cityExpanded = false }
                ) {
                    PairlyCatalog.citiesForState(selectedState).forEach { ct ->
                        DropdownMenuItem(
                            text = { Text(ct) },
                            onClick = {
                                selectedCity = ct
                                cityExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 500) bio = it },
                label = { Text("Bio") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Looking for", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RelationshipPreference.entries.take(3).forEach { pref ->
                    FilterChip(
                        selected = relationshipPref == pref.wireValue,
                        onClick = { relationshipPref = pref.wireValue },
                        label = { Text(pref.wireValue) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RelationshipPreference.entries.drop(3).forEach { pref ->
                    FilterChip(
                        selected = relationshipPref == pref.wireValue,
                        onClick = { relationshipPref = pref.wireValue },
                        label = { Text(pref.wireValue) }
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = promptExpanded,
                onExpandedChange = { promptExpanded = !promptExpanded }
            ) {
                OutlinedTextField(
                    value = promptQuestion,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Profile Prompt") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(promptExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = promptExpanded,
                    onDismissRequest = { promptExpanded = false }
                ) {
                    PairlyCatalog.promptQuestions.forEach { q ->
                        DropdownMenuItem(
                            text = { Text(q) },
                            onClick = {
                                promptQuestion = q
                                promptExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = promptAnswer,
                onValueChange = { if (it.length <= 280) promptAnswer = it },
                label = { Text("Prompt Answer") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Interests", style = MaterialTheme.typography.titleMedium)
            SelectableTagGroup(
                options = PairlyCatalog.interests,
                selected = interests,
                maxSelection = 6,
                onToggle = { tag ->
                    interests = if (tag in interests) interests - tag else interests + tag
                }
            )

            Text("Hobbies", style = MaterialTheme.typography.titleMedium)
            SelectableTagGroup(
                options = PairlyCatalog.hobbies,
                selected = hobbies,
                maxSelection = 6,
                onToggle = { tag ->
                    hobbies = if (tag in hobbies) hobbies - tag else hobbies + tag
                }
            )

            Text("Languages", style = MaterialTheme.typography.titleMedium)
            SelectableTagGroup(
                options = PairlyCatalog.languages,
                selected = languages,
                maxSelection = 6,
                onToggle = { tag ->
                    languages = if (tag in languages) languages - tag else languages + tag
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Photos (${photos.size}/6)", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = photos.size < 6 && !photoBusy
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (photoBusy) "Loading..." else "Add Photo")
                }
            }

            if (photos.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (photos.size > 3) 240.dp else 120.dp)
                ) {
                    itemsIndexed(photos) { idx, photo ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            PairlyPhotoImage(
                                photoSource = photo,
                                fallbackName = displayName,
                                contentDescription = "Profile photo ${idx + 1}",
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = {
                                    photos = photos.toMutableList().also { it.removeAt(idx) }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Delete photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val promptList = if (promptAnswer.isNotBlank()) {
                        listOf(ProfilePrompt(promptQuestion, promptAnswer.trim()))
                    } else {
                        emptyList()
                    }
                    onSave(
                        displayName,
                        selectedCity,
                        selectedState,
                        bio,
                        relationshipPref,
                        interests.toList(),
                        hobbies.toList(),
                        languages.toList(),
                        promptList,
                        photos
                    )
                },
                enabled = !isBusy && !photoBusy && displayName.trim().length >= 2,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_profile_edits_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedUsersSheet(
    blockedUsers: List<Pair<UserBlock, UserProfile?>>,
    onUnblock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Blocked Users", style = MaterialTheme.typography.headlineMedium)
            if (blockedUsers.isEmpty()) {
                Text(
                    text = "You haven't blocked anyone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                blockedUsers.forEach { (block, profile) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = profile?.displayName ?: "Blocked Member",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(onClick = { onUnblock(block.blockedUid) }) {
                                Text("Unblock")
                            }
                        }
                    }
                }
            }
        }
    }
}
