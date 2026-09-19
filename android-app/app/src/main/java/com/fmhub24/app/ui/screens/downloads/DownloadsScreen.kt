package com.fmhub24.app.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onPlayOffline: (String, String) -> Unit
) {
    val downloads by viewModel.downloads.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", color = Color.White) },
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
        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Downloaded videos will appear here", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads, key = { it.id }) { item ->
                    Card(
                        onClick = { onPlayOffline(item.sourceUrl, item.name) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = item.posterUrl,
                                contentDescription = item.name,
                                modifier = Modifier.size(width = 72.dp, height = 96.dp).background(Color(0xFF222222)),
                                contentScale = ContentScale.Crop
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.name, color = Color.White, maxLines = 2)
                                item.episodeName?.let { Text(it, color = Color.Gray, modifier = Modifier.padding(top = 4.dp)) }
                                Text("Available offline", color = Color(0xFF64DD8A), modifier = Modifier.padding(top = 6.dp))
                            }
                            IconButton(onClick = { viewModel.delete(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete download", tint = Color(0xFFFF6B6B))
                            }
                        }
                    }
                }
            }
        }
    }
}
