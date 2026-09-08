package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.BentoBg
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCard
import com.example.ui.theme.BentoError
import com.example.ui.theme.BentoGold
import com.example.ui.theme.BentoHero
import com.example.ui.theme.BentoHeroText
import com.example.ui.theme.BentoTextMain
import com.example.ui.theme.BentoTextSub
import com.example.ui.theme.CyberQuestTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    makeFullScreenImmersive()

    setContent {
      CyberQuestTheme {
        CyberQuestGameScreen(activity = this)
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      makeFullScreenImmersive()
    }
  }

  private fun makeFullScreenImmersive() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.hide(WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }
}

/**
 * Android JavaScript interface bridge for tactile haptics and game lifecycle.
 */
class CyberQuestBridge(private val context: Context) {

  private val vibrator: Vibrator? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      vibratorManager?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  } catch (e: Exception) {
    null
  }

  @JavascriptInterface
  fun vibrate(type: String) {
    try {
      val vib = vibrator ?: return
      if (!vib.hasVibrator()) return

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        when (type) {
          "click" -> vib.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
          "correct" -> vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 40, 60), -1))
          "wrong" -> vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 80), -1))
          "teleport" -> vib.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
          "levelup" -> vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 30, 40, 30, 100), -1))
          else -> vib.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        }
      } else {
        @Suppress("DEPRECATION")
        vib.vibrate(25)
      }
    } catch (e: Exception) {
      // Ignored if device lacks vibration capability
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CyberQuestGameScreen(activity: ComponentActivity) {
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var isLoading by remember { mutableStateOf(true) }
  var isPauseDialogOpen by remember { mutableStateOf(false) }

  // System back button handler
  BackHandler {
    webViewRef?.let { wv ->
      wv.evaluateJavascript("window.closeAnyOpenModal ? window.closeAnyOpenModal() : false") { result ->
        val wasModalClosed = result != null && result.contains("true")
        if (!wasModalClosed) {
          isPauseDialogOpen = true
        }
      }
    } ?: run {
      isPauseDialogOpen = true
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(BentoBg)
      .testTag("game_screen_container")
  ) {
    // Hardware accelerated WebView container
    AndroidView(
      modifier = Modifier
        .fillMaxSize()
        .testTag("cyber_quest_webview"),
      factory = { ctx ->
        WebView(ctx).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          setLayerType(View.LAYER_TYPE_HARDWARE, null)
          setBackgroundColor(0xFF1C1B1F.toInt())

          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
          }

          addJavascriptInterface(CyberQuestBridge(ctx), "AndroidBridge")

          webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
              super.onPageFinished(view, url)
              isLoading = false
            }
          }

          webChromeClient = WebChromeClient()

          loadUrl("file:///android_asset/cyberquest/index.html")
          webViewRef = this
        }
      },
      update = { wv ->
        webViewRef = wv
      }
    )

    // Startup Loading Screen
    AnimatedVisibility(
      visible = isLoading,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize()
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(BentoBg),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          CircularProgressIndicator(
            color = BentoHero,
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "INITIALIZING CYBER DEFENSE KERNEL...",
            color = BentoHero,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // Pause / Quick Menu Dialog (Landscape Form with Bento Grid Styling)
    if (isPauseDialogOpen) {
      Dialog(onDismissRequest = { isPauseDialogOpen = false }) {
        Box(
          modifier = Modifier
            .width(460.dp)
            .background(BentoCard, RoundedCornerShape(24.dp))
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
            .padding(24.dp)
            .testTag("pause_dialog")
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "CYBER QUEST // PAUSED",
              color = BentoTextMain,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 2.sp,
              fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Facility simulation paused. Select an option:",
              color = BentoTextSub,
              fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = { isPauseDialogOpen = false },
                colors = ButtonDefaults.buttonColors(
                  containerColor = BentoHero,
                  contentColor = BentoHeroText
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("btn_resume")
              ) {
                Text(
                  text = "RESUME",
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  letterSpacing = 1.sp
                )
              }

              OutlinedButton(
                onClick = {
                  isPauseDialogOpen = false
                  webViewRef?.reload()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                  contentColor = BentoGold
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                  brush = androidx.compose.ui.graphics.SolidColor(BentoBorder)
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("btn_reload")
              ) {
                Text(
                  text = "RESTART",
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  letterSpacing = 1.sp
                )
              }

              OutlinedButton(
                onClick = {
                  isPauseDialogOpen = false
                  activity.finish()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                  contentColor = BentoError
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                  brush = androidx.compose.ui.graphics.SolidColor(BentoBorder)
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("btn_exit")
              ) {
                Text(
                  text = "EXIT",
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  letterSpacing = 1.sp
                )
              }
            }
          }
        }
      }
    }

  }

  // Cleanup webview when Composable is disposed
  DisposableEffect(Unit) {
    onDispose {
      webViewRef?.destroy()
    }
  }
}
