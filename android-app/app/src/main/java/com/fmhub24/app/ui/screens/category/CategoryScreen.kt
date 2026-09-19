package com.fmhub24.app.ui.screens.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fmhub24.app.data.aggregation.ContentDeduplicator
import com.fmhub24.app.ui.components.ContentCard
import com.fmhub24.app.ui.theme.OrangeAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    providerName: String,
    categoryName: String,
    viewModel: CategoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (String, String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(providerName, categoryName) { viewModel.load(providerName, categoryName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryName, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        containerColor = Color.Black
    ) { padding ->
        when (val current = state) {
            CategoryViewModel.UiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OrangeAccent)
            }
            is CategoryViewModel.UiState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("This category is temporarily unavailable", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("Please try again or go back to another category.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                    Button(
                        onClick = { viewModel.load(providerName, categoryName) },
                        modifier = Modifier.padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    ) { Text("Try again") }
                }
            }
            is CategoryViewModel.UiState.Success -> {
                if (current.list.list.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nothing here yet", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text("This source did not return any items for this category.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                            Button(
                                onClick = { viewModel.load(providerName, categoryName) },
                                modifier = Modifier.padding(top = 16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                            ) { Text("Refresh") }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(current.list.list, key = { ContentDeduplicator.key(it) }) { item ->
                            ContentCard(item = item, modifier = Modifier.fillMaxWidth()) {
                                onNavigateToDetails(item.url, item.apiName)
                            }
                        }
                    }
                }
            }
        }
    }
}
