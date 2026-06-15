package com.massapay.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.massapay.android.core.model.Transaction
import com.massapay.android.network.repository.MassaRepository
import com.massapay.android.security.storage.SecureStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BalanceMonitorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val secureStorage: SecureStorage,
    private val massaRepository: MassaRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result {
        val address = secureStorage.getActiveWallet() ?: return ListenableWorker.Result.success()

        return when (val historyResult = massaRepository.getTransactionHistory(address)) {
            is com.massapay.android.core.util.Result.Success -> {
                historyResult.data
                    .firstOrNull { it.isRecentIncoming(address) && !wasAlreadyNotified(it.hash) }
                    ?.let { transaction ->
                        markNotified(transaction.hash)
                        showIncomingTransactionNotification(transaction)
                    }
                ListenableWorker.Result.success()
            }
            is com.massapay.android.core.util.Result.Error -> ListenableWorker.Result.retry()
            is com.massapay.android.core.util.Result.Loading -> ListenableWorker.Result.success()
        }
    }

    private fun Transaction.isRecentIncoming(address: String): Boolean {
        val twentyMinutesAgo = System.currentTimeMillis() - 20 * 60 * 1000
        return to == address &&
            hash.startsWith("incoming_") &&
            timestamp >= twentyMinutesAgo
    }

    private fun wasAlreadyNotified(hash: String): Boolean {
        return prefs().getString("last_notified_incoming_hash", null) == hash
    }

    private fun markNotified(hash: String) {
        prefs().edit().putString("last_notified_incoming_hash", hash).apply()
    }

    private fun prefs() = applicationContext.getSharedPreferences(
        "background_balance_monitor",
        Context.MODE_PRIVATE
    )

    private fun showIncomingTransactionNotification(transaction: Transaction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val channelId = "incoming_transactions"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Incoming transactions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when your Massa wallet receives balance"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.let { launchIntent ->
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

        val notification = android.app.Notification.Builder(applicationContext, channelId)
            .setSmallIcon(com.massapay.android.ui.R.drawable.brand_logo)
            .setContentTitle("Balance received")
            .setContentText("You received ${transaction.amount} MAS")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(transaction.hash.hashCode(), notification)
    }
}
