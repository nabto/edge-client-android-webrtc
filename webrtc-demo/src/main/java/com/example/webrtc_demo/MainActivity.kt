package com.example.webrtc_demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nabto.edge.client.webrtc.EdgeVideoTrack
import com.nabto.edge.client.webrtc.EdgeVideoView
import com.nabto.edge.client.webrtc.EdgeWebrtcManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DemoApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoApp() {
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WebRTC Demo") },
                actions = {
                    TextButton(onClick = {
                        if (navController.currentDestination?.route != "settings") {
                            navController.navigate("settings")
                        }
                    }) {
                        Text("Settings")
                    }
                }
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "video",
            modifier = Modifier.padding(padding)
        ) {
            composable("video") { VideoScreen() }
            composable("settings") { SettingsScreen(onDone = { navController.popBackStack() }) }
        }
    }
}

@Composable
fun VideoScreen(viewModel: DemoViewModel = viewModel()) {
    val context = LocalContext.current
    val statusLog by viewModel.statusLog.collectAsState()
    val videoTrack by viewModel.videoTrack.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.connect(
            DemoSettings.productId(context),
            DemoSettings.deviceId(context),
            DemoSettings.sct(context)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        var videoView by remember { mutableStateOf<EdgeVideoView?>(null) }
        AndroidView(
            factory = { ctx ->
                EdgeVideoView(ctx).also {
                    EdgeWebrtcManager.getInstance().initVideoView(it)
                    videoView = it
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        // (Re)attach the remote video track to the view whenever either changes.
        DisposableEffect(videoTrack, videoView) {
            val track: EdgeVideoTrack? = videoTrack
            val view = videoView
            if (track != null && view != null) {
                track.add(view)
            }
            onDispose {
                if (track != null && view != null) {
                    track.remove(view)
                }
            }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(statusLog.size) {
            if (statusLog.isNotEmpty()) {
                listState.animateScrollToItem(statusLog.size - 1)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            items(statusLog) { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var productId by remember { mutableStateOf(DemoSettings.productId(context)) }
    var deviceId by remember { mutableStateOf(DemoSettings.deviceId(context)) }
    var sct by remember { mutableStateOf(DemoSettings.sct(context)) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
        OutlinedTextField(
            value = productId,
            onValueChange = { productId = it },
            label = { Text("Product ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = deviceId,
            onValueChange = { deviceId = it },
            label = { Text("Device ID") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        OutlinedTextField(
            value = sct,
            onValueChange = { sct = it },
            label = { Text("Server Connect Token") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        Button(
            onClick = {
                DemoSettings.save(context, productId, deviceId, sct)
                onDone()
            },
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 16.dp)
        ) {
            Text("Save")
        }
    }
}
