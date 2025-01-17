package com.task.sm.chromecast

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.gms.cast.framework.CastContext
import com.task.sm.chromecast.navigation.NavGraph
import com.task.sm.chromecast.navigation.Route
import com.task.sm.chromecast.screens.HomeScreen
import com.task.sm.chromecast.ui.theme.ChromeCastTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        castContext = CastContext.getSharedInstance(this)
        enableEdgeToEdge()
        setContent {
            ChromeCastTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .padding(innerPadding)
                    ) {
                        NavGraph(startDestination = Route.ChromeCastAppNavigation.route)
                    }
                }
            }
        }
    }

    companion object {
        var castContext: CastContext? = null
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ChromeCastTheme {
        Greeting("Android")
    }
}
