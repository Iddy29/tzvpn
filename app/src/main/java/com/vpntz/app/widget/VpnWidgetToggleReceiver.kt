package com.vpntz.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.vpntz.app.domain.model.ConnectionState
import com.vpntz.app.presentation.MainActivity
import com.vpntz.app.service.VpnConnectionManager
import com.vpntz.app.util.AppLog as Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VpnWidgetToggleReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "VpnWidgetToggleReceiver"
        const val ACTION_TOGGLE_VPN = "com.vpntz.app.widget.TOGGLE_VPN"
    }

    @Inject
    lateinit var connectionManager: VpnConnectionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE_VPN) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        scope.launch {
            try {
                if (!::connectionManager.isInitialized) {
                    return@launch
                }

                when (connectionManager.connectionState.value) {
                    is ConnectionState.Connected,
                    is ConnectionState.Connecting -> {
                        connectionManager.disconnect()
                    }
                    is ConnectionState.Disconnected,
                    is ConnectionState.Error -> {
                        val profile = connectionManager.getActiveProfile()
                            ?: connectionManager.getLastConnectedProfile()
                            ?: return@launch

                        val vpnIntent = VpnService.prepare(appContext)
                        if (vpnIntent != null) {
                            val appIntent = Intent(appContext, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            appContext.startActivity(appIntent)
                            return@launch
                        }

                        connectionManager.connect(profile)
                    }
                    is ConnectionState.Disconnecting -> Unit
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle widget toggle", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}