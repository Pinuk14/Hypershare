package com.hypershare.ui.group

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver (exported="false") handling notification inline actions
 * for admin approval/decline of join requests.
 *
 * Registered in AndroidManifest.xml as:
 *   <receiver android:name=".ui.group.GroupJoinRequestReceiver"
 *             android:enabled="true" android:exported="false" />
 */
class GroupJoinRequestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: return
        val joiningUserId = intent.getStringExtra(EXTRA_JOINING_USER_ID) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return

        Log.d(TAG, "GroupJoinRequestAction: action=$action groupId=$groupId joiningUser=$joiningUserId")

        when (action) {
            ACTION_APPROVE -> {
                // TODO: Wire to GroupManager.addMember() to approve the join request
                Log.d(TAG, "Admin approved join request from $joiningUserId for group $groupId")
            }
            ACTION_DECLINE -> {
                Log.d(TAG, "Admin declined join request from $joiningUserId for group $groupId")
            }
        }

        // Dismiss the notification
        try {
            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            notifManager?.cancel(("joinreq_${groupId}_$joiningUserId").hashCode())
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "GroupJoinReqRcvr"
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_JOINING_USER_ID = "extra_joining_user_id"
        const val EXTRA_ACTION = "extra_action"
        const val ACTION_APPROVE = "APPROVE"
        const val ACTION_DECLINE = "DECLINE"
    }
}
