package com.viejitos

import android.provider.ContactsContract.CommonDataKinds.Phone
import android.app.Activity
import android.content.Intent
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap

class ContactsModule(private val context: ReactApplicationContext) :
  ReactContextBaseJavaModule(context) {

  private var pickerPromise: Promise? = null
  private val pickerRequestCode = 7412
  private val activityListener: ActivityEventListener = object : BaseActivityEventListener() {
    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
      if (requestCode != pickerRequestCode) return
      val promise = pickerPromise ?: return
      pickerPromise = null
      if (resultCode != Activity.RESULT_OK || data?.data == null) {
        promise.resolve(null)
        return
      }
      try {
        val uri = data.data!!
        context.contentResolver.query(uri, arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.PHOTO_THUMBNAIL_URI, Phone.NUMBER), null, null, null)?.use { cursor ->
          if (!cursor.moveToFirst()) {
            promise.resolve(null)
            return
          }
          val item = Arguments.createMap()
          item.putString("id", cursor.getString(cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)))
          item.putString("name", cursor.getString(cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)) ?: "Contacto")
          val photoIndex = cursor.getColumnIndex(Phone.PHOTO_THUMBNAIL_URI)
          if (photoIndex >= 0 && !cursor.isNull(photoIndex)) item.putString("photoUri", cursor.getString(photoIndex))
          val number = cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER))
          val numbers = Arguments.createArray()
          if (!number.isNullOrBlank()) numbers.pushString(number)
          item.putArray("phoneNumbers", numbers)
          promise.resolve(item)
        } ?: promise.resolve(null)
      } catch (error: Exception) {
        promise.reject("CONTACT_PICK_FAILED", "No se pudo leer el contacto seleccionado.", error)
      }
    }
  }

  init { context.addActivityEventListener(activityListener) }

  override fun getName(): String = "ContactsModule"

  @ReactMethod
  fun pickContact(promise: Promise) {
    if (pickerPromise != null) {
      promise.reject("CONTACT_PICK_BUSY", "Ya hay un selector de contactos abierto.")
      return
    }
    val activity = currentActivity
    if (activity == null) {
      promise.reject("NO_ACTIVITY", "No hay una pantalla disponible para abrir la agenda.")
      return
    }
    try {
      pickerPromise = promise
      activity.startActivityForResult(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI), pickerRequestCode)
    } catch (error: Exception) {
      pickerPromise = null
      promise.reject("CONTACT_PICK_FAILED", "No se pudo abrir la agenda del teléfono.", error)
    }
  }

  private class Entry(val id: String, val name: String, val photo: String?) {
    val phones = ArrayList<String>()
  }

  /** Una sola consulta a la tabla de teléfonos (antes era una consulta por contacto). */
  @ReactMethod
  fun getContacts(promise: Promise) {
    try {
      val entries = LinkedHashMap<String, Entry>()
      context.contentResolver.query(
        Phone.CONTENT_URI,
        arrayOf(
          Phone.CONTACT_ID,
          Phone.DISPLAY_NAME,
          Phone.PHOTO_THUMBNAIL_URI,
          Phone.NUMBER,
          Phone.IS_PRIMARY
        ),
        null,
        null,
        Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
      )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
        val photoIndex = cursor.getColumnIndexOrThrow(Phone.PHOTO_THUMBNAIL_URI)
        val numberIndex = cursor.getColumnIndexOrThrow(Phone.NUMBER)
        val primaryIndex = cursor.getColumnIndexOrThrow(Phone.IS_PRIMARY)

        while (cursor.moveToNext()) {
          val id = cursor.getString(idIndex) ?: continue
          val entry = entries.getOrPut(id) {
            Entry(
              id,
              cursor.getString(nameIndex) ?: "Sin nombre",
              if (cursor.isNull(photoIndex)) null else cursor.getString(photoIndex)
            )
          }
          val number = cursor.getString(numberIndex)?.trim()
          if (!number.isNullOrEmpty() && !entry.phones.contains(number)) {
            if (cursor.getInt(primaryIndex) != 0) {
              entry.phones.add(0, number)
            } else {
              entry.phones.add(number)
            }
          }
        }
      }

      val result: WritableArray = Arguments.createArray()
      for (entry in entries.values) {
        val item: WritableMap = Arguments.createMap()
        item.putString("id", entry.id)
        item.putString("name", entry.name)
        if (entry.photo != null) item.putString("photoUri", entry.photo)
        val phones: WritableArray = Arguments.createArray()
        for (number in entry.phones) phones.pushString(number)
        item.putArray("phoneNumbers", phones)
        result.pushMap(item)
      }
      promise.resolve(result)
    } catch (error: Exception) {
      promise.reject(
        "CONTACTS_READ_FAILED",
        "No se pudieron leer los contactos del teléfono.",
        error
      )
    }
  }
}
