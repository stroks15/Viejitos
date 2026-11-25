package com.viejitos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class MedicineBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    try {
      val prefs=context.getSharedPreferences("feros_medicine",Context.MODE_PRIVATE)
      val alarms=org.json.JSONObject(prefs.getString("alarms","{}") ?: "{}")
      val iterator=alarms.keys()
      while(iterator.hasNext()){
        val id=iterator.next()
        val item=alarms.optJSONObject(id) ?: continue
        val daysJson=item.optString("days","[]")
        val days=org.json.JSONArray(daysJson)
        if(days.length()==0)continue
        val time=try{org.json.JSONObject(prefs.getString("times","{}") ?: "{}").optJSONObject(id)}catch(_:Exception){null}
        val hour=time?.optInt("hour",8)?:8
        val minute=time?.optInt("minute",0)?:0
        val now=Calendar.getInstance();val target=Calendar.getInstance();var found=false
        for(offset in 0..7){
          target.timeInMillis=now.timeInMillis;target.add(Calendar.DAY_OF_YEAR,offset)
          val weekday=target.get(Calendar.DAY_OF_WEEK)-1
          var matches=false
          for(i in 0 until days.length())if(days.optInt(i,-1)==weekday)matches=true
          target.set(Calendar.HOUR_OF_DAY,hour);target.set(Calendar.MINUTE,minute);target.set(Calendar.SECOND,0);target.set(Calendar.MILLISECOND,0)
          if(matches&&target.timeInMillis>now.timeInMillis){found=true;break}
        }
        if(found)MedicineAlarmReceiver.schedule(context,id,item.optString("name"),item.optString("dose"),target.timeInMillis,item.optString("photo").takeIf{it.isNotBlank()},daysJson,item.optString("kind","medicine"))
      }
    }catch(_:Exception){}
  }
}