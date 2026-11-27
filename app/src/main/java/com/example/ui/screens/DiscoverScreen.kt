package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DiscoveryFilter
import com.example.data.model.DistanceTier
import com.example.data.model.PairlyCatalog
import com.example.data.model.RelationshipPreference
import com.example.data.model.ReportReason
import com.example.data.model.SwipeAction
import com.example.data.model.UserProfile
import com.example.ui.components.PairlyBrandMark
import com.example.ui.components.PairlyPhotoImage
import com.example.ui.components.ProfileDetailSheet
import com.example.ui.components.ReportUserDialog
import com.example.ui.components.SelectableTagGroup
import com.example.ui.theme.PairlyDarkBackground
import com.example.ui.theme.PairlyPink
import com.example.ui.theme.PairlyPurple
import com.example.ui.theme.SuperLikeVioletGold
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DiscoverScreen(
    myProfile: UserProfile?,
    deckState: UiState<List<UserProfile>>,
    currentFilter: DiscoveryFilter,
    onApplyFilter: (DiscoveryFilter) -> Unit,
    onSwipe: (UserProfile, SwipeAction) -> Unit,
    onBlockUser: (String) -> Unit,
    onReportUser: (String, ReportReason, String) -> Unit
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    var inspectedProfile by remember { mutableStateOf<UserProfile?>(null) }
    var reportTarget by remember { mutableStateOf<UserProfile?>(null) }

    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PairlyBrandMark()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (myProfile?.incognito == true || myProfile?.hidden == true) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = PairlyPink
                                )
                                Text(
                                    text = if (myProfile.hidden) "Hidden" else "Incognito",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    Surface(
                        onClick = { showFilterSheet = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("discover_filter_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "Discovery filters"
                            )
                        }
                    }
                }
            }

            when (deckState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PairlyPink)
                    }
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = deckState.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is UiState.Success -> {
                    val profiles = deckState.data
                    if (profiles.isEmpty()) {
                        EmptyDiscoveryCard(
                            onResetFilters = { onApplyFilter(DiscoveryFilter()) },
                            onOpenFilters = { showFilterSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    } else {
                        val topProfile = profiles.first()
                        val nextProfile = profiles.getOrNull(1)

                        val offsetX = remember(topProfile.userId) { Animatable(0f) }
                        val offsetY = remember(topProfile.userId) { Animatable(0f) }

                        fun triggerAnimatedSwipe(action: SwipeAction) {
                            scope.launch {
                                val targetX = when (action) {
                                    SwipeAction.PASS -> -950f
                                    SwipeAction.LIKE -> 950f
                                    SwipeAction.SUPER_LIKE -> 0f
                                }
                                val targetY = if (action == SwipeAction.SUPER_LIKE) -950f else 80f
                                launch {
                                    offsetX.animateTo(targetX, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                offsetY.animateTo(targetY, spring(stiffness = Spring.StiffnessMediumLow))
                                onSwipe(topProfile, action)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Next card peek underneath
                            if (nextProfile != null) {
                                DiscoveryProfileCard(
                                    profile = nextProfile,
                                    onInspect = {},
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                                        .alpha(0.6f)
                                )
                            }

                            // Top interactive swipe card
                            val dragX = offsetX.value
                            val dragY = offsetY.value
                            val likeRatio = (dragX / 260f).coerceIn(0f, 1f)
                            val passRatio = (-dragX / 260f).coerceIn(0f, 1f)
                            val superRatio = (-dragY / 240f).coerceIn(0f, 1f)

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                                    .rotate((dragX / 35f).coerceIn(-16f, 16f))
                                    .pointerInput(topProfile.userId) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                scope.launch {
                                                    when {
                                                        offsetX.value > 240f -> triggerAnimatedSwipe(SwipeAction.LIKE)
                                                        offsetX.value < -240f -> triggerAnimatedSwipe(SwipeAction.PASS)
                                                        offsetY.value < -220f && abs(offsetX.value) < 180f ->
                                                            triggerAnimatedSwipe(SwipeAction.SUPER_LIKE)
                                                        else -> {
                                                            launch { offsetX.animateTo(0f, spring(dampingRatio = 0.7f)) }
                                                            offsetY.animateTo(0f, spring(dampingRatio = 0.7f))
                                                        }
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                scope.launch {
                                                    launch { offsetX.animateTo(0f, spring()) }
                                                    offsetY.animateTo(0f, spring())
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                scope.launch {
                                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                                    offsetY.snapTo(offsetY.value + dragAmount.y * 0.6f)
                                                }
                                            }
                                        )
                                    }
                                    .testTag("discovery_top_card")
                            ) {
                                DiscoveryProfileCard(
                                    profile = topProfile,
                                    onInspect = { inspectedProfile = topProfile },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Swipe Stamp Overlays
                                if (likeRatio > 0.1f) {
                                    SwipeStampBadge(
                                        label = "LIKE",
                                        color = PairlyPink,
                                        alpha = likeRatio,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(24.dp)
                                            .rotate(-14f)
                                    )
                                }
                                if (passRatio > 0.1f) {
                                    SwipeStampBadge(
                                        label = "PASS",
                                        color = Color(0xFFB0A8BF),
                                        alpha = passRatio,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(24.dp)
                                            .rotate(14f)
                                    )
                                }
                                if (superRatio > 0.15f) {
                                    SwipeStampBadge(
                                        label = "SUPER LIKE",
                                        color = SuperLikeVioletGold,
                                        alpha = superRatio,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 110.dp)
                                    )
                                }
                            }
                        }

                        // Action Buttons Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { triggerAnimatedSwipe(SwipeAction.PASS) },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .size(62.dp)
                                    .testTag("pass_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Pass profile",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = { triggerAnimatedSwipe(SwipeAction.SUPER_LIKE) },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 6.dp,
                                border = BorderStroke(1.5.dp, SuperLikeVioletGold),
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("super_like_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = "Super Like profile",
                                        tint = SuperLikeVioletGold,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = { triggerAnimatedSwipe(SwipeAction.LIKE) },
                                shape = CircleShape,
                                color = PairlyPink,
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .size(66.dp)
                                    .testTag("like_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = "Like profile",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        DiscoveryFilterSheet(
            initialFilter = currentFilter,
            onDismiss = { showFilterSheet = false },
            onApply = { updated ->
                onApplyFilter(updated)
                showFilterSheet = false
            }
        )
    }

    inspectedProfile?.let { prof ->
        ProfileDetailSheet(
            profile = prof,
            onDismiss = { inspectedProfile = null },
            onBlock = {
                onBlockUser(prof.userId)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoveryProfileCard(
    profile: UserProfile,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = modifier.clickable(onClick = onInspect)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PairlyPhotoImage(
                photoSource = profile.photos.firstOrNull(),
                fallbackName = profile.displayName,
                contentDescription = "${profile.displayName} profile photo",
                modifier = Modifier.fillMaxSize()
            )

            // Gradient scrim for crisp legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.48f to Color.Transparent,
                                0.78f to PairlyDarkBackground.copy(alpha = 0.78f),
                                1.0f to PairlyDarkBackground.copy(alpha = 0.96f)
                            )
                        )
                    )
            )

            // Top right info button
            Surface(
                onClick = onInspect,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "View full profile",
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${profile.displayName}, ${profile.age}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = PairlyPurple.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = profile.relationshipPreference,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = PairlyPink,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${profile.city}, ${profile.state}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                if (profile.bio.isNotBlank()) {
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (profile.interests.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profile.interests.take(4).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeStampBadge(
    label: String,
    color: Color,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.35f),
        modifier = modifier
            .alpha(alpha)
            .border(2.5.dp, color, RoundedCornerShape(10.dp))
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun EmptyDiscoveryCard(
    onResetFilters: () -> Unit,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = PairlyPink,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "You're all caught up",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No more visible profiles match your current filters right now. Adjust your age, city, or distance preferences to discover more people across India.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenFilters) {
                    Text("Adjust Filters")
                }
                Button(onClick = onResetFilters) {
                    Text("Show Pan-India")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryFilterSheet(
    initialFilter: DiscoveryFilter,
    onDismiss: () -> Unit,
    onApply: (DiscoveryFilter) -> Unit
) {
    var minAge by remember { mutableFloatStateOf(initialFilter.minAge.toFloat()) }
    var maxAge by remember { mutableFloatStateOf(initialFilter.maxAge.toFloat()) }
    var selectedState by remember { mutableStateOf(initialFilter.selectedState) }
    var stateExpanded by remember { mutableStateOf(false) }
    var selectedCity by remember { mutableStateOf(initialFilter.selectedCity) }
    var cityExpanded by remember { mutableStateOf(false) }
    var selectedPref by remember { mutableStateOf(initialFilter.relationshipPreference) }
    var selectedInterests by remember { mutableStateOf(initialFilter.selectedInterests) }
    var distanceTier by remember { mutableStateOf(initialFilter.distanceTier) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Discovery Filters", style = MaterialTheme.typography.headlineMedium)
                TextButton(
                    onClick = { onApply(DiscoveryFilter()) }
                ) {
                    Text("Reset")
                }
            }

            Column {
                Text(
                    text = "Age Range: ${minAge.roundToInt()} – ${maxAge.roundToInt()}",
                    style = MaterialTheme.typography.titleMedium
                )
                RangeSlider(
                    value = minAge..maxAge,
                    onValueChange = { range ->
                        minAge = range.start
                        maxAge = range.endInclusive
                    },
                    valueRange = 18f..70f
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Approximate Distance", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DistanceTier.entries.forEach { tier ->
                        FilterChip(
                            selected = distanceTier == tier,
                            onClick = { distanceTier = tier },
                            label = { Text(tier.label) }
                        )
                    }
                }
            }

            // State filter
            val allStateOptions = listOf("All India") + PairlyCatalog.allStates
            ExposedDropdownMenuBox(
                expanded = stateExpanded,
                onExpandedChange = { stateExpanded = !stateExpanded }
            ) {
                OutlinedTextField(
                    value = selectedState,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Filter by State") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stateExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = stateExpanded,
                    onDismissRequest = { stateExpanded = false }
                ) {
                    allStateOptions.forEach { st ->
                        DropdownMenuItem(
                            text = { Text(st) },
                            onClick = {
                                selectedState = st
                                selectedCity = "All Cities"
                                stateExpanded = false
                            }
                        )
                    }
                }
            }

            // City filter
            val cityOptions = if (selectedState == "All India") {
                listOf("All Cities")
            } else {
                listOf("All Cities") + PairlyCatalog.citiesForState(selectedState)
            }
            ExposedDropdownMenuBox(
                expanded = cityExpanded,
                onExpandedChange = { cityExpanded = !cityExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCity,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Filter by City") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cityExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = cityExpanded,
                    onDismissRequest = { cityExpanded = false }
                ) {
                    cityOptions.forEach { ct ->
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Relationship Preference", style = MaterialTheme.typography.titleMedium)
                val prefs = listOf("All") + RelationshipPreference.entries.map { it.wireValue }
                SelectableTagGroup(
                    options = prefs,
                    selected = setOf(selectedPref),
                    maxSelection = 2,
                    onToggle = { selectedPref = it }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Shared Interests", style = MaterialTheme.typography.titleMedium)
                SelectableTagGroup(
                    options = PairlyCatalog.interests.take(12),
                    selected = selectedInterests,
                    maxSelection = 6,
                    onToggle = { tag ->
                        selectedInterests = if (tag in selectedInterests) {
                            selectedInterests - tag
                        } else {
                            selectedInterests + tag
                        }
                    }
                )
            }

            Button(
                onClick = {
                    onApply(
                        DiscoveryFilter(
                            minAge = minAge.roundToInt().coerceAtLeast(18),
                            maxAge = maxAge.roundToInt().coerceAtLeast(minAge.roundToInt()),
                            selectedState = selectedState,
                            selectedCity = selectedCity,
                            relationshipPreference = selectedPref,
                            selectedInterests = selectedInterests,
                            distanceTier = distanceTier
                        )
                    )
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("apply_filters_button")
            ) {
                Text("Apply Filters", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun NewMatchCelebrationDialog(
    matchedProfile: UserProfile,
    onSendMessage: () -> Unit,
    onKeepSwiping: () -> Unit
) {
    Dialog(
        onDismissRequest = onKeepSwiping,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PairlyDarkBackground.copy(alpha = 0.96f),
                            PairlyPurple.copy(alpha = 0.88f)
                        )
                    )
                )
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PairlyPink.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = "MUTUAL MATCH",
                        color = PairlyPink,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                Text(
                    text = "It's a Match!",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                PairlyPhotoImage(
                    photoSource = matchedProfile.photos.firstOrNull(),
                    fallbackName = matchedProfile.displayName,
                    contentDescription = "${matchedProfile.displayName} match photo",
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .border(3.dp, PairlyPink, CircleShape)
                )

                Text(
                    text = "You and ${matchedProfile.displayName} liked each other.",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onSendMessage,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PairlyPink,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("match_send_message_button")
                ) {
                    Text("Send a Message", style = MaterialTheme.typography.titleMedium)
                }

                TextButton(onClick = onKeepSwiping) {
                    Text("Keep Discovering", color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
    }
}
