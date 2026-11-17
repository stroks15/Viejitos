package com.viejitos

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)
    // Edge-to-edge: el contenido llega hasta los bordes; React Native aplica los márgenes seguros.
    WindowCompat.setDecorFitsSystemWindows(window, false)
    hideStatusBar()
    // Launcher (HOME): "Atrás" nunca cierra ni congela la app.
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        handleBack()
      }
    })
    // Ask Android to make FerOs abues the default Home app on first launch.
    window.decorView.post { requestDefaultLauncherRole() }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) hideStatusBar()
  }

  private fun hideStatusBar() {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.statusBars())
  }

  /** Avisa a JS (evento "LauncherBack") para que regrese a Inicio; nunca llama a finish(). */
  private fun handleBack() {
    LauncherEvents.emit("LauncherBack", null)
  }

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun onBackPressed() {
    handleBack()
  }

  private fun requestDefaultLauncherRole() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = getSystemService(RoleManager::class.java)
      if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
          !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
        startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), 4101)
      }
    } else {
      try {
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
      } catch (_: Exception) {
        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
      }
    }
  }

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "FerOsAbues"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}
