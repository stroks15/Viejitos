package com.viejitos

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)
    // Ask Android to make FerOs abues the default Home app on first launch.
    window.decorView.post { requestDefaultLauncherRole() }
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
