package com.pranav.drsti.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pranav.drsti.database.entity.PlaceEntity
import com.pranav.drsti.ui.viewmodel.PlaceViewModel

/** Fully local/offline, editable place database (spec §15). No Google Maps dependency. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreen(
    viewModel: PlaceViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Places", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showAdd = !showAdd }) { 
                        Icon(Icons.Default.Add, contentDescription = "Add place")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding)) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = state.query, onValueChange = viewModel::onQueryChange,
                    label = { Text("Search city or town") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = ShapeDefaults.Medium
                )
            }

            if (showAdd) {
                AddPlaceForm(onSave = { entity -> viewModel.save(entity) { showAdd = false } })
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.searchResults, key = { it.id }) { place ->
                    PlaceItem(
                        place = place,
                        onDelete = { viewModel.delete(place) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceItem(place: PlaceEntity, onDelete: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeDefaults.Medium,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        ListItem(
            headlineContent = { Text(place.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { 
                Text(
                    text = "${place.state ?: ""}, ${place.country}\nLat: ${place.latitude}, Lon: ${place.longitude} (${place.timezone})",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                if (place.isUserCreated) {
                    IconButton(onClick = onDelete) { 
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) 
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}

@Composable
private fun AddPlaceForm(onSave: (PlaceEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var tz by remember { mutableStateOf("Asia/Kolkata") }

    ElevatedCard(
        modifier = Modifier.padding(16.dp),
        shape = ShapeDefaults.Large
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add New Location", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Place Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = lat, onValueChange = { lat = it },
                    label = { Text("Latitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lon, onValueChange = { lon = it },
                    label = { Text("Longitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            
            OutlinedTextField(
                value = tz, onValueChange = { tz = it },
                label = { Text("Timezone (IANA ID)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Button(
                onClick = {
                    val latD = lat.toDoubleOrNull(); val lonD = lon.toDoubleOrNull()
                    if (name.isNotBlank() && latD != null && lonD != null) {
                        onSave(PlaceEntity(name = name, latitude = latD, longitude = lonD, timezone = tz, isUserCreated = true))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && lat.toDoubleOrNull() != null && lon.toDoubleOrNull() != null,
                shape = ShapeDefaults.Medium
            ) { Text("Save Place") }
        }
    }
}

