package org.bottiger.podcast.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.support.annotation.RequiresApi

import org.bottiger.podcast.R
import org.bottiger.podcast.provider.Subscription
import org.bottiger.podcast.provider.Subscription.STATUS_UNSUBSCRIBED

/**
 * Created by aplb on 20-06-2017.
 */
object NotificationChannels {

    const val CHANNEL_ID_PLAYER         = "player_channel"
    const val CHANNEL_ID_SUBSCRIPTION   = "subscription_channel"
    const val CHANNEL_ID_ALL_EPISODES   = "episodes_channel"

    @RequiresApi(26)
    fun createPlayerChannel(argContext: Context) {
        // The user-visible name of the channel.
        val resources = argContext.resources;
        val name = resources.getString(R.string.channel_name_player)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID_PLAYER, name, importance)
        channel.setSound(null, null);

        val notificationManager = argContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        //notificationManager.deleteNotificationChannel(CHANNEL_ID_PLAYER);

        notificationManager.createNotificationChannel(channel);
    }

    @RequiresApi(26)
    fun createEpisodesChannel(argContext: Context) {
        // The user-visible name of the channel.
        val resources = argContext.resources;
        val name = resources.getString(R.string.channel_name_episodes)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID_ALL_EPISODES, name, importance)

        channel.setShowBadge(true)
        channel.setSound(null, null);

        val notificationManager = argContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel);
    }

    @RequiresApi(26)
    fun getSubscriptionUpdatedChannel(argContext: Context, argSubscription: Subscription) {
        // The user-visible name of the channel.
        val resources = argContext.resources;
        val name = argSubscription.title;
        val group = resources.getString(R.string.channel_name_subscriptions)
        val importance = NotificationManager.IMPORTANCE_MIN
        val channelId = getChannelId(argSubscription)
        val channel = NotificationChannel(channelId, name, importance)

        channel.group = group
        channel.setShowBadge(true)

        val notificationManager = argContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val remove = argSubscription.status.toInt() == STATUS_UNSUBSCRIBED

        if (remove) {
            notificationManager.deleteNotificationChannel(channelId)
        } else {
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getChannelId(argSubscription: Subscription) : String {
        return CHANNEL_ID_SUBSCRIPTION + argSubscription.id
    }

}
