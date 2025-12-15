package com.homeplanner.debug

import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * Helper для получения уникального идентификатора устройства.
 * 
 * Используется для идентификации устройства при отправке логов на сервер,
 * что позволяет серверу разделять логи от разных устройств.
 */
object DeviceIdHelper {
    /**
     * Получить уникальный идентификатор устройства.
     * 
     * Использует следующую стратегию:
     * 1. Пытается получить ANDROID_ID (уникальный для устройства и приложения на Android 8.0+)
     * 2. Если ANDROID_ID недоступен или невалиден, генерирует UUID и сохраняет его в SharedPreferences
     * 3. При последующих запусках использует сохраненный UUID
     * 
     * @param context Application context
     * @return Уникальный идентификатор устройства
     */
    fun getDeviceId(context: Context): String {
        // Попытка получить ANDROID_ID
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        
        // ANDROID_ID может быть null или иметь некорректное значение (9774d56d682e549c)
        // Используем его только если он валидный
        if (androidId != null && androidId.isNotEmpty() && androidId != "9774d56d682e549c") {
            return "android_$androidId"
        }
        
        // Если ANDROID_ID недоступен, используем сохраненный UUID
        val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        val savedDeviceId = prefs.getString("device_id", null)
        
        if (savedDeviceId != null && savedDeviceId.isNotEmpty()) {
            return savedDeviceId
        }
        
        // Генерируем новый UUID и сохраняем его
        val newDeviceId = "uuid_${UUID.randomUUID()}"
        prefs.edit().putString("device_id", newDeviceId).apply()
        
        return newDeviceId
    }
}
