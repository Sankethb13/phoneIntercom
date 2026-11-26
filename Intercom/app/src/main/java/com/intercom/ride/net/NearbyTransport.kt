package com.intercom.ride.net

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

/**
 * Transport choice: Google Nearby Connections, P2P_POINT_TO_POINT.
 * It discovers over BLE/Bluetooth, opens the link over Bluetooth, then
 * transparently upgrades to a Wi-Fi Direct / hotspot link when possible —
 * all with no internet, SIM or access point. Links are encrypted (UKEY2).
 */
object NearbyConfig {
    const val SERVICE_ID = "com.intercom.ride.v1"
    val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT
}

private fun describe(e: Exception): String {
    val code = (e as? ApiException)?.statusCode
    return if (code != null) "${ConnectionsStatusCodes.getStatusCodeString(code)} ($code)" else (e.message ?: e.javaClass.simpleName)
}

private fun isBenign(e: Exception): Boolean {
    val code = (e as? ApiException)?.statusCode
    return code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING ||
        code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING ||
        code == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT
}

/** Advertises this phone and discovers other Intercom phones. */
class DeviceDiscoveryService(
    private val client: ConnectionsClient,
    private val lifecycle: ConnectionLifecycleCallback,
    private val listener: Listener,
) {
    interface Listener {
        fun onPeerFound(endpointId: String, info: EndpointInfo)
        fun onPeerLost(endpointId: String)
        fun onDiscoveryError(message: String)
    }

    var advertising = false; private set
    var discovering = false; private set

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != NearbyConfig.SERVICE_ID) return
            val parsed = EndpointInfo.decode(info.endpointInfo) ?: return
            listener.onPeerFound(endpointId, parsed)
        }

        override fun onEndpointLost(endpointId: String) = listener.onPeerLost(endpointId)
    }

    fun start(self: EndpointInfo) {
        if (!advertising) {
            advertising = true
            client.startAdvertising(
                self.encode(), NearbyConfig.SERVICE_ID, lifecycle,
                AdvertisingOptions.Builder().setStrategy(NearbyConfig.STRATEGY).build(),
            ).addOnFailureListener { e ->
                if (!isBenign(e)) { advertising = false; listener.onDiscoveryError("advertising: ${describe(e)}") }
            }
        }
        if (!discovering) {
            discovering = true
            client.startDiscovery(
                NearbyConfig.SERVICE_ID, discoveryCallback,
                DiscoveryOptions.Builder().setStrategy(NearbyConfig.STRATEGY).build(),
            ).addOnFailureListener { e ->
                if (!isBenign(e)) { discovering = false; listener.onDiscoveryError("discovery: ${describe(e)}") }
            }
        }
    }

    fun stop() {
        if (advertising) client.stopAdvertising()
        if (discovering) client.stopDiscovery()
        advertising = false
        discovering = false
    }

    fun restart(self: EndpointInfo) { stop(); start(self) }
}

/** Opens, accepts, rejects and tears down links, and moves bytes over them. */
class PeerConnectionManager(context: Context, private val listener: Listener) {
    interface Listener {
        fun onConnectionInitiated(endpointId: String, info: EndpointInfo?, authDigits: String, incoming: Boolean)
        fun onConnected(endpointId: String)
        fun onRejected(endpointId: String)
        fun onConnectionError(endpointId: String, message: String)
        fun onDisconnected(endpointId: String)
        fun onMessage(endpointId: String, message: Message)
    }

    val client: ConnectionsClient = Nearby.getConnectionsClient(context)

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val msg = Message.decode(bytes) ?: return
            listener.onMessage(endpointId, msg)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            listener.onConnectionInitiated(
                endpointId, EndpointInfo.decode(info.endpointInfo), info.authenticationDigits, info.isIncomingConnection,
            )
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            when (resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> listener.onConnected(endpointId)
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> listener.onRejected(endpointId)
                else -> listener.onConnectionError(
                    endpointId, resolution.status.statusMessage ?: "status ${resolution.status.statusCode}",
                )
            }
        }

        override fun onDisconnected(endpointId: String) = listener.onDisconnected(endpointId)
    }

    fun request(self: EndpointInfo, endpointId: String) {
        client.requestConnection(self.encode(), endpointId, lifecycle)
            .addOnFailureListener { e -> if (!isBenign(e)) listener.onConnectionError(endpointId, describe(e)) }
    }

    fun accept(endpointId: String) {
        client.acceptConnection(endpointId, payloadCallback)
            .addOnFailureListener { e -> if (!isBenign(e)) listener.onConnectionError(endpointId, describe(e)) }
    }

    fun reject(endpointId: String) {
        client.rejectConnection(endpointId)
    }

    fun send(endpointId: String, message: Message) {
        client.sendPayload(endpointId, Payload.fromBytes(Message.encode(message)))
            .addOnFailureListener { e -> Log.d(TAG, "send failed: ${describe(e)}") }
    }

    fun disconnect(endpointId: String) = client.disconnectFromEndpoint(endpointId)

    fun shutdown() = client.stopAllEndpoints()

    private companion object { const val TAG = "PeerConnection" }
}
