package com.pranav.drsti.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pranav.drsti.database.entity.PersonEntity
import com.pranav.drsti.database.entity.PlaceEntity
import com.pranav.drsti.ui.viewmodel.PersonViewModel
import com.pranav.drsti.ui.viewmodel.PlaceViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Profile / birth-data screen (spec §14, §16, §17 "Complete My Panchang" workflow).
 * Birth-time uncertainty is preserved as metadata rather than pretending an
 * approximate time is exact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    personViewModel: PersonViewModel,
    placeViewModel: PlaceViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val personState by personViewModel.state.collectAsState()
    val placeState by placeViewModel.state.collectAsState()

    var showEditor by remember { mutableStateOf(personState.people.isEmpty()) }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf(LocalDate.now().minusYears(25)) }
    var tob by remember { mutableStateOf(LocalTime.of(6, 0)) }
    var uncertainty by remember { mutableStateOf("10") }
    var selectedPlace by remember { mutableStateOf<PlaceEntity?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Birth Profiles", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (!showEditor) {
                        IconButton(onClick = { showEditor = true }) { Icon(Icons.Filled.Add, contentDescription = "Add person") }
                    }
                }
            )
        }
    ) { padding ->
        if (showEditor) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("New Profile", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("Enter birth details to unlock personalized Vedic analysis.", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedCard(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Date of Birth", style = MaterialTheme.typography.labelSmall)
                            Text(dob.format(DateTimeFormatter.ISO_LOCAL_DATE), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    OutlinedCard(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Time of Birth", style = MaterialTheme.typography.labelSmall)
                            Text(tob.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                OutlinedTextField(
                    value = uncertainty, onValueChange = { uncertainty = it },
                    label = { Text("Uncertainty (\u00B1 minutes)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Birth Location", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = placeState.query,
                    onValueChange = { placeViewModel.onQueryChange(it) },
                    label = { Text("Search City/Town") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.LocationOn, null) }
                )

                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(placeState.searchResults, key = { it.id }) { place ->
                            ListItem(
                                headlineContent = { Text(place.name) },
                                supportingContent = { Text("${place.state ?: ""}, ${place.country}") },
                                trailingContent = { if (selectedPlace?.id == place.id) Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { selectedPlace = place },
                                colors = ListItemDefaults.colors(containerColor = if (selectedPlace?.id == place.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showEditor = false }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val place = selectedPlace
                            if (name.isNotBlank() && place != null) {
                                val now = Instant.now().toString()
                                personViewModel.save(
                                    PersonEntity(
                                        name = name,
                                        dateOfBirthIso = dob.toString(),
                                        timeOfBirthIso = tob.toString(),
                                        birthTimeUncertaintyMinutes = uncertainty.toIntOrNull(),
                                        placeId = place.id,
                                        latitude = place.latitude,
                                        longitude = place.longitude,
                                        timezone = place.timezone,
                                        isActive = true,
                                        createdAt = now,
                                        updatedAt = now
                                    )
                                )
                                showEditor = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank() && selectedPlace != null
                    ) {
                        Text("Create Profile")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (personState.people.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No birth profiles saved yet.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                items(personState.people, key = { it.id }) { person ->
                    ProfileCard(
                        person = person,
                        onUse = { personViewModel.setActive(person.id) },
                        onDelete = { personViewModel.delete(person) }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dob.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        dob = Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = tob.hour,
            initialMinute = tob.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    tob = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

@Composable
private fun ProfileCard(
    person: PersonEntity,
    onUse: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (person.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${person.dateOfBirthIso} \u2022 ${person.timeOfBirthIso}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (person.isActive) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Active") },
                        icon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!person.isActive) {
                    TextButton(onClick = onUse) {
                        Text("Set Active")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
