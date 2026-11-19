package pl.adrian.ptaki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pl.adrian.ptaki.ui.AppRoot
import pl.adrian.ptaki.ui.PtakiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PtakiTheme {
                AppRoot()
            }
        }
    }
}
