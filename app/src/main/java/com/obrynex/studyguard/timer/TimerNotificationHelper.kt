package com.obrynex.studyguard.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.obrynex.studyguard.MainActivity
import com.obrynex.studyguard.R

/**
 * Helper for creating and managing timer notifications.
 */
object TimerNotificationHelper {

    const val CHANNEL_ID = "study_timer_channel"
    private const val COMPLETION_CHANNEL_ID = "timer_completion_channel"

    /** Creates the notification channel (required on Android O+). */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelName = context.getString(R.string.channel_name)
        val channelDescription = context.getString(R.string.channel_description)
        val completionChannelName = context.getString(R.string.completion_channel_name)
        val completionChannelDescription = context.getString(R.string.completion_channel_description)

        val channel = NotificationChannel(
            CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = channelDescription
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val completionChannel = NotificationChannel(
            COMPLETION_CHANNEL_ID,
            completionChannelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = completionChannelDescription
        }
        manager.createNotificationChannel(completionChannel)
    }

    /** Builds the timer foreground notification with action buttons. */
    fun buildTimerNotification(
        context: Context,
        title: String,
        text: String,
        showPause: Boolean,
        showResume: Boolean,
        showStop: Boolean
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (title.isBlank()) context.getString(R.string.notification_title) else title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (showPause) {
            val pauseIntent = PendingIntent.getService(
                context,
                1,
                Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_PAUSE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_notification,
                context.getString(R.string.action_pause),
                pauseIntent
            )
        }

        if (showResume) {
            val resumeIntent = PendingIntent.getService(
                context,
                2,
                Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_RESUME
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_notification,
                context.getString(R.string.action_resume),
                resumeIntent
            )
        }

        if (showStop) {
            val stopIntent = PendingIntent.getService(
                context,
                3,
                Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_STOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_notification,
                context.getString(R.string.action_stop),
                stopIntent
            )
        }

        return builder.build()
    }

    /** Shows a completion notification when a study session finishes. */
    fun showCompletionNotification(context: Context) {
        val openIntent = PendingIntent.getActivity(
            context,
            4,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.timer_complete))
            .setContentText(context.getString(R.string.timer_complete_message))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(TimerService.NOTIFICATION_ID + 1, notification)
    }
}

/**
 * Broadcast receiver for timer notification action buttons.
 */
class TimerNotificationReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        val serviceIntent = android.content.Intent(context, TimerService::class.java).apply {
            action = intent.action
        }
        context.startTimerService(serviceIntent)
    }
}
