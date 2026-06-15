package com.massapay.android.network.agentbridge

sealed interface AgentConnectionState {
    object Disconnected : AgentConnectionState
    object Connecting : AgentConnectionState
    data class Connected(val endpoint: String? = null) : AgentConnectionState
    data class Error(val message: String) : AgentConnectionState
}

data class AgentNodeStatus(
    val connected: Boolean = false,
    val version: String? = null,
    val currentCycle: Long? = null,
    val connectedPeers: Int? = null
)

data class AgentStakingInfo(
    val finalRolls: Int = 0,
    val candidateRolls: Int = 0,
    val activeRolls: Int = 0,
    val balance: String = "0"
)

data class StakingRewards(
    val totalRewards: String = "0",
    val cycleRewards: List<CycleReward> = emptyList()
)

data class CycleReward(
    val cycle: Long,
    val reward: String
)
