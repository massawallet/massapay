package com.massapay.android.ui.agentbridge

import androidx.lifecycle.ViewModel
import com.massapay.android.network.agentbridge.AgentConnectionState
import com.massapay.android.network.agentbridge.AgentNodeStatus
import com.massapay.android.network.agentbridge.AgentStakingInfo
import com.massapay.android.network.agentbridge.StakingRewards
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class AgentBridgeViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(AgentBridgeUiState())
    val uiState: StateFlow<AgentBridgeUiState> = _uiState.asStateFlow()

    fun connectWithQrContent(qrContent: String) {
        _uiState.update {
            it.copy(
                connectionState = AgentConnectionState.Error("Massa Agent bridge is not available in this build"),
                error = "Massa Agent bridge is not available in this build"
            )
        }
    }

    fun disconnect() {
        _uiState.update { AgentBridgeUiState() }
    }

    fun fetchNodeStatus() = Unit

    fun fetchStakingInfo(address: String) = Unit

    fun fetchStakingAddresses() = Unit

    fun fetchRewards(address: String) = Unit

    fun startStakingAuto() {
        _uiState.update {
            it.copy(
                operationResult = AgentOperationResult(
                    isSuccess = false,
                    title = "Agent unavailable",
                    message = "Massa Agent bridge is not available in this build.",
                    operationType = StakingOpType.REGISTER_STAKING
                )
            )
        }
    }

    fun removeStakingKey(address: String) {
        _uiState.update {
            it.copy(
                operationResult = AgentOperationResult(
                    isSuccess = false,
                    title = "Agent unavailable",
                    message = "Massa Agent bridge is not available in this build.",
                    operationType = StakingOpType.REMOVE_STAKING
                )
            )
        }
    }

    fun clearOperationSuccess() {
        _uiState.update { it.copy(operationSuccess = null) }
    }

    fun clearOperationResult() {
        _uiState.update { it.copy(operationResult = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class AgentBridgeUiState(
    val connectionState: AgentConnectionState = AgentConnectionState.Disconnected,
    val nodeStatus: AgentNodeStatus? = null,
    val stakingInfo: AgentStakingInfo? = null,
    val stakingAddresses: List<String> = emptyList(),
    val walletAddress: String? = null,
    val rewards: StakingRewards? = null,
    val isProcessingOperation: Boolean = false,
    val operationSuccess: String? = null,
    val operationResult: AgentOperationResult? = null,
    val error: String? = null
)

data class AgentOperationResult(
    val isSuccess: Boolean,
    val title: String,
    val message: String,
    val operationType: StakingOpType
)

enum class StakingOpType {
    REGISTER_STAKING,
    REMOVE_STAKING,
    BUY_ROLLS,
    SELL_ROLLS
}
