package com.viejitos

import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile

/** Servicio de llamadas del sistema; Android lo enlaza al asignar ROLE_DIALER. */
class FerOsInCallService : InCallService() {
  private var previousRingVolume: Int? = null

  private fun applyRingtoneProtection() {
    val enabled = getSharedPreferences("feros_settings", MODE_PRIVATE)
      .getBoolean("ringtone_protection", false)
    if (!enabled || previousRingVolume != null) return
    try {
      val audio = getSystemService(AUDIO_SERVICE) as AudioManager
      previousRingVolume = audio.getStreamVolume(AudioManager.STREAM_RING)
      audio.setStreamVolume(AudioManager.STREAM_RING,
        audio.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
    } catch (_: Exception) { previousRingVolume = null }
  }

  private fun restoreRingtoneVolume() {
    val previous = previousRingVolume ?: return
    try {
      val audio = getSystemService(AUDIO_SERVICE) as AudioManager
      audio.setStreamVolume(AudioManager.STREAM_RING, previous, 0)
    } catch (_: Exception) {
    } finally { previousRingVolume = null }
  }

  override fun onCallAdded(call: Call) {
    super.onCallAdded(call)
    CallManager.attach(this, call)
    call.registerCallback(object : Call.Callback() {
      override fun onStateChanged(changedCall: Call, state: Int) {
        CallManager.updateState(state)
        if (state == Call.STATE_RINGING) applyRingtoneProtection()
        if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) restoreRingtoneVolume()
        CallActivity.current?.renderCallState(state)
        if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
          CallActivity.current?.finishAfterDisconnect()
        }
      }
      override fun onDetailsChanged(changedCall: Call, details: Call.Details) {
        CallActivity.current?.renderCallState(changedCall.state)
      }
    })
    val contact = lookupContact(call.details.handle)
    val intent = Intent(this, CallActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      putExtra(CallActivity.EXTRA_NAME, contact.first)
      putExtra(CallActivity.EXTRA_NUMBER, call.details.handle?.schemeSpecificPart ?: "")
      putExtra(CallActivity.EXTRA_PHOTO, contact.second)
      putExtra(CallActivity.EXTRA_STATE, call.state)
      putExtra(CallActivity.EXTRA_UNKNOWN, contact.first.isBlank() || contact.first == (call.details.handle?.schemeSpecificPart ?: ""))
    }
    try { startActivity(intent) } catch (_: Exception) {
      // Algunos fabricantes bloquean el inicio de Activities desde segundo plano.
    }
  }

  override fun onCallRemoved(call: Call) {
    restoreRingtoneVolume()
    super.onCallRemoved(call)
    CallManager.detach(call)
    CallActivity.current?.finishAfterDisconnect()
  }

  private fun lookupContact(handle: Uri?): Pair<String, String?> {
    val number = handle?.schemeSpecificPart ?: return Pair("", null)
    return try {
      val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
      contentResolver.query(uri,
        arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI),
        null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val name = cursor.getString(0) ?: number
          val photo = if (!cursor.isNull(1)) cursor.getString(1) else null
          return Pair(name, photo)
        }
      }
      Pair(number, null)
    } catch (_: SecurityException) { Pair(number, null) }
      catch (_: Exception) { Pair(number, null) }
  }
}

object CallManager {
  @Volatile private var activeCall: Call? = null
  @Volatile private var inCallService: InCallService? = null
  @Volatile var currentState: Int = Call.STATE_DISCONNECTED
    private set

  fun attach(service: InCallService, call: Call) {
    inCallService = service
    activeCall = call
    currentState = call.state
  }
  fun detach(call: Call) {
    if (activeCall === call) {
      activeCall = null
      inCallService = null
      currentState = Call.STATE_DISCONNECTED
    }
  }
  fun updateState(state: Int) { currentState = state }
  fun answer() { activeCall?.answer(VideoProfile.STATE_AUDIO_ONLY) }
  fun disconnect() { activeCall?.disconnect() }
  fun setSpeaker(enabled: Boolean) {
    inCallService?.setAudioRoute(
      if (enabled) android.telecom.CallAudioState.ROUTE_SPEAKER
      else android.telecom.CallAudioState.ROUTE_EARPIECE
    )
  }
  fun setMuted(muted: Boolean) { inCallService?.setMuted(muted) }
}
