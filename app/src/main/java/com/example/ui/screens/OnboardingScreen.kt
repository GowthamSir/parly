package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.AgeCalculator
import com.example.data.model.PairlyCatalog
import com.example.data.model.ProfilePrompt
import com.example.data.model.RelationshipPreference
import com.example.ui.components.PairlyPhotoImage
import com.example.ui.components.SelectableTagGroup
import com.example.ui.theme.PairlyPink
import com.example.ui.theme.PairlyPurple
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    isBusy: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onSignOut: () -> Unit,
    onComplete: (
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
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf("") }
    var dobIso by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    var selectedState by remember { mutableStateOf("Maharashtra") }
    var stateExpanded by remember { mutableStateOf(false) }
    var selectedCity by remember { mutableStateOf("Mumbai") }
    var cityExpanded by remember { mutableStateOf(false) }

    var bio by remember { mutableStateOf("") }
    var relationshipPref by remember { mutableStateOf(RelationshipPreference.LONG_TERM.wireValue) }
    var selectedInterests by remember { mutableStateOf(setOf("Indie Cinema", "Filter Coffee")) }
    var selectedHobbies by remember { mutableStateOf(setOf("Photography")) }
    var selectedLanguages by remember { mutableStateOf(setOf("English", "Hindi")) }

    var promptQuestion by remember { mutableStateOf(PairlyCatalog.promptQuestions.first()) }
    var promptExpanded by remember { mutableStateOf(false) }
    var promptAnswer by remember { mutableStateOf("") }

    var photos by remember { mutableStateOf<List<String>>(emptyList()) }
    var photoBusy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val calculatedAge = remember(dobIso) {
        if (dobIso.isBlank()) null else AgeCalculator.calculateAgeFromIso(dobIso)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            photoBusy = true
            localError = null
            val updated = photos.toMutableList()
            for (uri in uris) {
                if (updated.size >= 6) break
                ImageEncoder.encodeUriToDataUri(context, uri)
                    .onSuccess { dataUri -> updated.add(dataUri) }
                    .onFailure { err -> localError = err.message ?: "Failed to process image." }
            }
            photos = updated
            photoBusy = false
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = millis
                            }
                            dobIso = AgeCalculator.formatDobIso(
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH) + 1,
                                cal.get(Calendar.DAY_OF_MONTH)
                            )
                            localError = null
                        }
                        showDatePicker = false
                    }
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 0) {
                        IconButton(onClick = { step-- }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous step")
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    Text(
                        text = "Create Profile (${step + 1}/3)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("onboarding_logout_button")
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sign out")
                    }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (step + 1) / 3f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = PairlyPink
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val activeError = localError ?: errorMessage
                if (!activeError.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = activeError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                when (step) {
                    0 -> {
                        Text(
                            text = "Tell us who you are",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Text(
                            text = "PAIRLY is exclusively for LGBTQ+ adults aged 18+ in India. Your exact birth date stays private.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { if (it.length <= 50) displayName = it },
                            label = { Text("Display Name") },
                            placeholder = { Text("e.g., Aarav, Kabir, Rohan") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_name_input")
                        )

                        OutlinedTextField(
                            value = if (dobIso.isBlank()) "" else "$dobIso" + (calculatedAge?.let { " ($it yrs)" } ?: ""),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Date of Birth (18+ required)") },
                            placeholder = { Text("Select your birth date") },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showDatePicker = true },
                                    modifier = Modifier.testTag("dob_picker_button")
                                ) {
                                    Icon(Icons.Filled.CalendarMonth, contentDescription = "Select date of birth")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true }
                                .testTag("onboarding_dob_field")
                        )

                        if (calculatedAge != null && calculatedAge < 18) {
                            Text(
                                text = "Sorry, you must be 18 or older to create a PAIRLY account.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        // State selector
                        ExposedDropdownMenuBox(
                            expanded = stateExpanded,
                            onExpandedChange = { stateExpanded = !stateExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedState,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("State / UT") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = stateExpanded,
                                onDismissRequest = { stateExpanded = false }
                            ) {
                                PairlyCatalog.allStates.forEach { stateName ->
                                    DropdownMenuItem(
                                        text = { Text(stateName) },
                                        onClick = {
                                            selectedState = stateName
                                            selectedCity = PairlyCatalog.citiesForState(stateName).firstOrNull().orEmpty()
                                            stateExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // City selector
                        val cities = PairlyCatalog.citiesForState(selectedState)
                        ExposedDropdownMenuBox(
                            expanded = cityExpanded,
                            onExpandedChange = { cityExpanded = !cityExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedCity,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("City") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = cityExpanded,
                                onDismissRequest = { cityExpanded = false }
                            ) {
                                cities.forEach { cityName ->
                                    DropdownMenuItem(
                                        text = { Text(cityName) },
                                        onClick = {
                                            selectedCity = cityName
                                            cityExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onClearError()
                                localError = null
                                when {
                                    displayName.trim().length < 2 ->
                                        localError = "Please enter a display name (at least 2 characters)."
                                    calculatedAge == null ->
                                        localError = "Please select your date of birth."
                                    calculatedAge < 18 ->
                                        localError = "You must be at least 18 years old to join PAIRLY."
                                    selectedCity.isBlank() || selectedState.isBlank() ->
                                        localError = "Please select your city and state."
                                    else -> step = 1
                                }
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("onboarding_next_step_1")
                        ) {
                            Text("Continue", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    1 -> {
                        Text(
                            text = "Your vibe & preferences",
                            style = MaterialTheme.typography.headlineLarge
                        )

                        OutlinedTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 500) bio = it },
                            label = { Text("About You (Bio)") },
                            placeholder = { Text("Share what makes you smile, your passions, or weekend rituals...") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_bio_input")
                        )

                        Text("Looking for", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RelationshipPreference.entries.take(3).forEach { pref ->
                                FilterChip(
                                    selected = relationshipPref == pref.wireValue,
                                    onClick = { relationshipPref = pref.wireValue },
                                    label = { Text(pref.wireValue) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RelationshipPreference.entries.drop(3).forEach { pref ->
                                FilterChip(
                                    selected = relationshipPref == pref.wireValue,
                                    onClick = { relationshipPref = pref.wireValue },
                                    label = { Text(pref.wireValue) }
                                )
                            }
                        }

                        Text("Interests (up to 6)", style = MaterialTheme.typography.titleMedium)
                        SelectableTagGroup(
                            options = PairlyCatalog.interests,
                            selected = selectedInterests,
                            maxSelection = 6,
                            onToggle = { tag ->
                                selectedInterests = if (tag in selectedInterests) selectedInterests - tag else selectedInterests + tag
                            }
                        )

                        Text("Hobbies (up to 6)", style = MaterialTheme.typography.titleMedium)
                        SelectableTagGroup(
                            options = PairlyCatalog.hobbies,
                            selected = selectedHobbies,
                            maxSelection = 6,
                            onToggle = { tag ->
                                selectedHobbies = if (tag in selectedHobbies) selectedHobbies - tag else selectedHobbies + tag
                            }
                        )

                        Text("Languages", style = MaterialTheme.typography.titleMedium)
                        SelectableTagGroup(
                            options = PairlyCatalog.languages,
                            selected = selectedLanguages,
                            maxSelection = 6,
                            onToggle = { tag ->
                                selectedLanguages = if (tag in selectedLanguages) selectedLanguages - tag else selectedLanguages + tag
                            }
                        )

                        Button(
                            onClick = { step = 2 },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("onboarding_next_step_2")
                        ) {
                            Text("Next: Prompts & Photos", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    2 -> {
                        Text(
                            text = "Prompts & Photos",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Text(
                            text = "Add a conversation starter and up to 6 photos from your gallery.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ExposedDropdownMenuBox(
                            expanded = promptExpanded,
                            onExpandedChange = { promptExpanded = !promptExpanded }
                        ) {
                            OutlinedTextField(
                                value = promptQuestion,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Profile Prompt") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = promptExpanded) },
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
                            label = { Text("Your Answer") },
                            placeholder = { Text("Make it easy to reply to...") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_prompt_answer")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Photos (${photos.size}/6)", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = photos.size < 6 && !photoBusy,
                                modifier = Modifier.testTag("add_photos_button")
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (photoBusy) "Processing..." else "Add Photos")
                            }
                        }

                        if (photos.isNotEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (photos.size > 3) 260.dp else 130.dp)
                            ) {
                                itemsIndexed(photos) { idx, photo ->
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(3f / 4f)
                                            .clip(RoundedCornerShape(14.dp))
                                    ) {
                                        PairlyPhotoImage(
                                            photoSource = photo,
                                            fallbackName = displayName,
                                            contentDescription = "Selected photo ${idx + 1}",
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
                                                contentDescription = "Remove photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val promptsList = if (promptAnswer.isNotBlank()) {
                                    listOf(ProfilePrompt(promptQuestion, promptAnswer.trim()))
                                } else {
                                    emptyList()
                                }
                                onComplete(
                                    displayName,
                                    dobIso,
                                    selectedCity,
                                    selectedState,
                                    bio,
                                    relationshipPref,
                                    selectedInterests.toList(),
                                    selectedHobbies.toList(),
                                    selectedLanguages.toList(),
                                    promptsList,
                                    photos
                                )
                            },
                            enabled = !isBusy && !photoBusy,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PairlyPurple,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("finish_onboarding_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Enter PAIRLY", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
