package com.fmhub24.app.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (String, String) -> Unit
) {
    val favorites by viewModel.favorites.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library", color = Color.White, fontWeight = FontWeight.Bold) },
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

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "💔", fontSize = 48.sp)
                    Text(
                        text = "No favorites yet",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "Your saved favorites will appear here.\nData is stored locally via Room.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favorites) { fav ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onNavigateToDetails(fav.url, fav.apiName) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = fav.posterUrl,
                                contentDescription = fav.name,
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A1A1A)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fav.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    maxLines = 2
                                )
                                Text(
                                    text = "${fav.type ?: "Unknown"} • ${fav.apiName}",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Text(
                                    text = "Added ${java.text.SimpleDateFormat("MMM dd, yyyy").format(java.util.Date(fav.addedAt))}",
                                    color = Color.Gray.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.removeFavorite(fav.url) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
