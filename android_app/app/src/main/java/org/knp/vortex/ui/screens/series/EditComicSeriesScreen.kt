package org.knp.vortex.ui.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.knp.vortex.ui.theme.DeepBackground
import org.knp.vortex.ui.components.AppHeader

@Composable
fun EditComicSeriesScreen(
    seriesName: String,
    onBack: () -> Unit,
    viewModel: EditComicSeriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val singlePhotoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> 
            if (uri != null) {
                viewModel.updateField(posterUrl = uri.toString())
            }
        }
    )

    LaunchedEffect(seriesName) {
        viewModel.loadSeries(seriesName)
    }

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            viewModel.resetSuccess()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Edit Metadata",
                onBack = onBack
            )
        },
        containerColor = DeepBackground
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.error != null) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Poster Image Picker
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .height(200.dp)
                                .width(135.dp) // ~2:3 aspect ratio
                                .background(Color.Gray.copy(alpha = 0.3f), shape = MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                             if (uiState.posterUrl.isNotBlank()) {
                                 coil.compose.AsyncImage(
                                     model = coil.request.ImageRequest.Builder(context)
                                         .data(uiState.posterUrl)
                                         .crossfade(true)
                                         .build(),
                                     contentDescription = "Poster",
                                     modifier = Modifier.fillMaxSize(),
                                     contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                 )
                             } else {
                                 Icon(
                                     Icons.Default.Add,
                                     contentDescription = "Add Poster",
                                     tint = Color.White,
                                     modifier = Modifier.size(48.dp)
                                 )
                             }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { 
                                singlePhotoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Change Poster")
                        }
                    }

                    OutlinedTextField(
                        value = uiState.year,
                        onValueChange = { viewModel.updateField(year = it) },
                        label = { Text("Year") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.Gray
                        )
                    )

                    OutlinedTextField(
                        value = uiState.genres,
                        onValueChange = { viewModel.updateField(genres = it) },
                        label = { Text("Genres (comma separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.Gray
                        )
                    )

                    OutlinedTextField(
                        value = uiState.plot,
                        onValueChange = { viewModel.updateField(plot = it) },
                        label = { Text("Plot") },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.Gray
                        ),
                        maxLines = 5
                    )

                    Button(
                        onClick = { viewModel.saveChanges(context) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}
