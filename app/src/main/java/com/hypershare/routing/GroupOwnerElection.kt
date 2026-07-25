package com.hypershare.routing

class GroupOwnerElection {

    /**
     * Compute Group Owner (GO) intent score (0..15) based on:
     * - Battery level (weight 40%)
     * - Active connectivity count (weight 30%)
     * - Connection stability / uptime (weight 30%)
     */
    fun calculateGoIntentScore(
        batteryPct: Int,
        connectedPeersCount: Int,
        uptimeMinutes: Long
    ): Int {
        val batteryScore = (batteryPct / 100.0) * 6
        val connectivityScore = minOf(connectedPeersCount / 5.0, 1.0) * 4.5
        val stabilityScore = minOf(uptimeMinutes / 60.0, 1.0) * 4.5

        val total = (batteryScore + connectivityScore + stabilityScore).toInt()
        return total.coerceIn(0, 15)
    }
}
