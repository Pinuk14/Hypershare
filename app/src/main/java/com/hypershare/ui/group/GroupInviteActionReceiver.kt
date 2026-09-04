package com.hypershare.ui.group

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver (exported="false") handling notification inline actions
 * for broadcast group invite Join/Decline.
 *
 * Registered in AndroidManifest.xml as:
 *   <receiver android:name=".ui.group.GroupInviteActionReceiver"
 *             android:enabled="true" android:exported="false" />
 */
class GroupInviteActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return

        Log.d(TAG, "GroupInviteAction: action=$action groupId=$groupId")

        when (action) {
            ACTION_JOIN -> {
                // TODO: Wire to GroupManager.handleGroupPacket() to accept the invite
                Log.d(TAG, "User tapped JOIN for group $groupId from notification")
            }
            ACTION_DECLINE -> {
                // Dismiss — no action needed beyond clearing the notification
                Log.d(TAG, "User tapped DECLINE for group $groupId from notification")
            }
        }

        // Dismiss the notification
        try {
            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            notifManager?.cancel(("invite_$groupId").hashCode())
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "GroupInviteActionRcvr"
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_ACTION = "extra_action"
        const val ACTION_JOIN = "JOIN"
        const val ACTION_DECLINE = "DECLINE"
    }
}
