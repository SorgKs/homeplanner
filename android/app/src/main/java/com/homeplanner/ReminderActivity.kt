package com.homeplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.RingtoneManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.WindowManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import android.media.Ringtone
import com.homeplanner.services.ReminderReceiver

class ReminderActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable showing over lock screen and turning screen on
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        
        // Make activity appear over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // Keep screen on and unlock
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Acquire wake lock to keep device awake
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "HomePlanner:ReminderWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes
        
        // Start playing alarm sound
        startAlarmSound()
        
        val title = intent.getStringExtra("title") ?: "Напоминание"
        val message = intent.getStringExtra("message") ?: "Время выполнить задачу"
        val taskId = if (intent.hasExtra("taskId")) intent.getIntExtra("taskId", -1) else -1
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ReminderScreen(
                        title = title,
                        message = message,
                        onDismiss = { 
                            stopAlarmSound()
                            releaseWakeLock()
                            finish() 
                        },
                        onSnooze = { 
                            stopAlarmSound()
                            releaseWakeLock()
                            scheduleSnooze(title, message, taskId, 10)
                            finish() 
                        }
                    )
                }
            }
        }
    }
    
    private fun startAlarmSound() {
        try {
            // Set audio mode to normal and adjust volume
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // Set to normal mode and ensure ringer is on
            audioManager.mode = AudioManager.MODE_NORMAL
            if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            
            // Get alarm URI
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            if (alarmUri != null) {
                Log.d("ReminderActivity", "Using sound URI: $alarmUri")
                
                // Use MediaPlayer with looping support
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@ReminderActivity, alarmUri)
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                    isLooping = true
                    setVolume(1.0f, 1.0f)
                    
                    // Set completion listener to restart if looping fails
                    setOnCompletionListener {
                        if (isLooping) {
                            try {
                                seekTo(0)
                                start()
                                Log.d("ReminderActivity", "Sound restarted")
                            } catch (e: Exception) {
                                Log.e("ReminderActivity", "Failed to restart sound", e)
                            }
                        }
                    }
                    
                    prepare()
                    start()
                    Log.d("ReminderActivity", "Alarm sound started via MediaPlayer (looping)")
                }
            } else {
                Log.w("ReminderActivity", "No alarm sound URI found")
                
                // Try Ringtone as last resort (no looping)
                try {
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    if (defaultUri != null) {
                        ringtone = RingtoneManager.getRingtone(this, defaultUri)
                        ringtone?.apply {
                            streamType = AudioManager.STREAM_ALARM
                            play()
                            Log.d("ReminderActivity", "Ringtone started (no looping)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ReminderActivity", "Failed to play ringtone", e)
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderActivity", "Failed to play alarm sound", e)
            e.printStackTrace()
        }
    }
    
    private fun stopAlarmSound() {
        try {
            ringtone?.stop()
            ringtone = null
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            Log.d("ReminderActivity", "Alarm sound stopped")
        } catch (e: Exception) {
            Log.e("ReminderActivity", "Failed to stop alarm sound", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            Log.d("ReminderActivity", "Wake lock released")
        } catch (e: Exception) {
            Log.e("ReminderActivity", "Failed to release wake lock", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
        releaseWakeLock()
    }

    private fun scheduleSnooze(title: String, message: String, taskId: Int, minutes: Int) {
        val ctx = applicationContext
        val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            if (taskId != -1) putExtra("taskId", taskId)
        }
        val pi = PendingIntent.getBroadcast(
            ctx,
            if (taskId != -1) taskId else System.currentTimeMillis().toInt(),
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarm.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}

@Composable
fun ReminderScreen(title: String, message: String, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDismiss) {
            Text("Сброс")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onSnooze) {
            Text("Отложить на 10 минут")
        }
    }
}

