package com.task.sm.chromecast.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.cast.framework.CastContext
import com.task.sm.chromecast.MainActivity.Companion.castContext
import com.task.sm.chromecast.R
import com.task.sm.chromecast.ui.theme.ChromeCastTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navHostController: NavHostController) {
    val context = LocalContext.current
    val homeViewModal: HomeViewModal = HomeViewModal(context)
    val sliderPosition = homeViewModal.playerControllerUiState.currentPosition

    LaunchedEffect(key1 = Unit) {
        homeViewModal.setMediaItems(
            listOf(
                VideoModal
                    (
                    url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    title = "BigBuckBunny",
                    id = 123456L
                )
            ), sliderPosition
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(text = "Player Screen")
            }, modifier = Modifier
                .shadow(6.dp), actions = {
                IconButton(onClick = {
                    if (castContext?.sessionManager?.currentCastSession == null) {
                        MediaRouteChooserDialog(
                            context,
                            androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
                        ).apply {
                            routeSelector = homeViewModal.mSelector
                        }.show()
                    } else {
                        MediaRouteControllerDialog(
                            context,
                            androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
                        ).apply {
                            println(
                                "......${mediaControlView?.id} >>>>>>> ${mediaSession?.token}"
                            )
                        }.show()
                    }
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cast),
                        contentDescription = " Cast"
                    )
                }
            })
        },
        content = {
            Column(modifier = Modifier.padding(it)) {
                ExoPlayer(viewModel = homeViewModal, castContext)

            }

        })
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ExoPlayer(viewModel: HomeViewModal, castContext: CastContext?) {
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)
    val mPlayer = viewModel.playerManager.value?.getCurrentPlayer()

    AndroidView(
        factory = {
            viewModel.playerView.apply {
                println(">>>>>>>>>>>>>>>>>>>@@@@>>${mPlayer}")
                player = mPlayer
                keepScreenOn = true
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(color = colorResource(id = R.color.black))
            .pointerInput(Unit) {
                // to detect gesture like tap
            },

        )
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.e("LIFECYCLE", "resumed")
                    viewModel.playerManager.value?.getCurrentPlayer()?.play()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    Log.e("LIFECYCLE", "paused")
                    Log.d(
                        "LIFECYCLE",
                        "____________________???${castContext?.sessionManager?.currentCastSession}"
                    )
                }

                Lifecycle.Event.ON_CREATE -> {}
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {
                    Log.e("LIFECYCLE", "distroy")
                }

                Lifecycle.Event.ON_ANY -> {
                    Log.e("LIFECYCLE", "any")
                }
            }
        }

        val lifecycle = lifecycleOwner.value.lifecycle
        lifecycle.addObserver(observer)
        onDispose {
            if (castContext?.sessionManager?.currentCastSession != null) {
                viewModel.playerManager.value?.releaseLocalPlayer()
            } else viewModel.playerManager.value?.resetMediaItemIndex()
            lifecycle.removeObserver(observer)
        }
    }

}

@Preview(showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    ChromeCastTheme {
        HomeScreen(rememberNavController())
    }
}

data class PlayerController(
    val currentPosition: Long = 0L
)

data class VideoModal(
    val url: String,
    val title: String,
    val id: Long
)