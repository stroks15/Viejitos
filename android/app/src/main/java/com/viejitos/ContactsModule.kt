package com.viejitos

import android.provider.ContactsContract
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap

class ContactsModule(private val context: ReactApplicationContext) :
  ReactContextBaseJavaModule(context) {

  override fun getName(): String = "ContactsModule"

  @ReactMethod
  fun getContacts(promise: Promise) {
    try {
      val result: WritableArray = Arguments.createArray()
      val resolver = context.contentResolver
      val projection = arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME,
        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
      )
      resolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        projection,
        ContactsContract.Contacts.HAS_PHONE_NUMBER + " = 1",
        null,
        ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
      )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
        val photoIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
        while (cursor.moveToNext()) {
          val contactId = cursor.getString(idIndex)
          val item: WritableMap = Arguments.createMap()
          item.putString("id", contactId)
          item.putString("name", cursor.getString(nameIndex) ?: "Sin nombre")
          if (!cursor.isNull(photoIndex)) item.putString("photoUri", cursor.getString(photoIndex))

          val phones: WritableArray = Arguments.createArray()
          resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
            arrayOf(contactId),
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY + " DESC"
          )?.use { phoneCursor ->
            val numberIndex = phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (phoneCursor.moveToNext()) {
              val number = phoneCursor.getString(numberIndex)?.trim()
              if (!number.isNullOrEmpty()) phones.pushString(number)
            }
          }
          item.putArray("phoneNumbers", phones)
          result.pushMap(item)
        }
      }
      promise.resolve(result)
    } catch (error: Exception) {
      promise.reject("CONTACTS_READ_FAILED", "No se pudieron leer los contactos del teléfono.", error)
    }
  }
}
