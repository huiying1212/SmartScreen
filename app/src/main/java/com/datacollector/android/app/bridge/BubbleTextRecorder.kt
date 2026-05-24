package com.datacollector.android.app.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.datacollector.android.data.repository.ReflectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens for the bubble-text broadcast emitted by [FloatingOverlayService]
 * and persists each reminder into the Room `reflection_message` table so
 * Home / History can show its history and weekly reports can summarise it.
 */
@Singleton
class BubbleTextRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reflectionRepo: ReflectionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("bubble_text") ?: return
            if (text.isBlank()) return
            val score = context.getSharedPreferences("llm_scoring_state", Context.MODE_PRIVATE)
                .getInt("current_score", 0)
            scope.launch {
                runCatching {
                    reflectionRepo.record("bubble", text, scoreAtTime = score)
                }
            }
        }
    }

    fun install() {
        val filter = IntentFilter("com.datacollector.android.BUBBLE_TEXT_UPDATED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }
}
