package com.markleaf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.markleaf.app.ui.navigation.MarkLeafNavGraph
import com.markleaf.app.ui.theme.MarkLeafTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MarkLeafApplication

        setContent {
            MarkLeafTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    MarkLeafNavGraph(
                        navController = navController,
                        repository = app.documentRepository,
                    )
                }
            }
        }
    }
}
