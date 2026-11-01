package ds.dsh.DeepSeek

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log

/**
 * UID-authenticated Binder endpoint delivered through the existing browsable trampoline.
 *
 * Android package visibility can hide a module provider or service from an unmodified host.
 * A framework ResultReceiver lets the trampoline return this Binder without putting credentials
 * in a URI. Every transaction verifies the real Binder caller UID against com.deepseek.chat;
 * merely obtaining the Binder grants no access.
 */
class PublicTunnelBinderBridge private constructor() {
    companion object {
        const val DESCRIPTOR = "ds.dsh.DeepSeek.IPublicTunnelBridge"
        const val TRANSACTION_STATUS = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_CONFIGURE = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_SET_REQUESTED = IBinder.FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_PINGGY_STATUS = IBinder.FIRST_CALL_TRANSACTION + 3
        const val TRANSACTION_SET_PINGGY_REQUESTED = IBinder.FIRST_CALL_TRANSACTION + 4
        const val TRANSACTION_CANCEL_PROVISIONING = IBinder.FIRST_CALL_TRANSACTION + 5

        private const val TARGET_PACKAGE = "com.deepseek.chat"
        private const val TAG = "DeekseepTunnelBridge"
        private val BRIDGE: IBinder = Bridge()

        @Volatile
        private var applicationContext: Context? = null

        @JvmStatic
        fun binder(context: Context?): IBinder {
            if (context != null) applicationContext = context.applicationContext
            return BRIDGE
        }

        private class Bridge : Binder() {
            @Throws(RemoteException::class)
            override fun onTransact(code: Int, data: Parcel, reply: Parcel, flags: Int): Boolean {
                if (code == INTERFACE_TRANSACTION) {
                    reply.writeString(DESCRIPTOR)
                    return true
                }
                if (code != TRANSACTION_STATUS
                    && code != TRANSACTION_CONFIGURE
                    && code != TRANSACTION_SET_REQUESTED
                    && code != TRANSACTION_PINGGY_STATUS
                    && code != TRANSACTION_SET_PINGGY_REQUESTED
                    && code != TRANSACTION_CANCEL_PROVISIONING
                ) {
                    return super.onTransact(code, data, reply, flags)
                }

                data.enforceInterface(DESCRIPTOR)
                val extras = data.readBundle(PublicTunnelBinderBridge::class.java.classLoader)
                val context = applicationContext
                val callingUid = Binder.getCallingUid()

                val result = when {
                    context == null -> Bundle().apply {
                        putBoolean("accepted", false)
                        putString("error", "module context is unavailable")
                    }
                    !uidOwnsPackage(context, callingUid, TARGET_PACKAGE) -> {
                        Log.w(TAG, "rejected public tunnel transaction from uid=$callingUid")
                        Bundle().apply {
                            putBoolean("accepted", false)
                            putString("error", "caller is not DeepSeek")
                        }
                    }
                    code == TRANSACTION_CONFIGURE ->
                        PublicTunnelManager.configure(context, extras)
                    code == TRANSACTION_SET_REQUESTED ->
                        PublicTunnelManager.setRequested(
                            context, extras?.getBoolean("enabled", false) ?: false
                        )
                    code == TRANSACTION_SET_PINGGY_REQUESTED ->
                        PinggyTunnelManager.setRequested(
                            context, extras?.getBoolean("enabled", false) ?: false
                        )
                    code == TRANSACTION_PINGGY_STATUS ->
                        PinggyTunnelManager.status(context)
                    code == TRANSACTION_CANCEL_PROVISIONING -> {
                        PublicTunnelManager.cancelProvisioning()
                        Bundle().apply { putBoolean("accepted", true) }
                    }
                    else -> PublicTunnelManager.status(context)
                }

                reply.writeNoException()
                reply.writeBundle(result)
                return true
            }
        }

        private fun uidOwnsPackage(context: Context, uid: Int, wanted: String): Boolean {
            return try {
                val packages = context.packageManager.getPackagesForUid(uid) ?: return false
                packages.any { it == wanted }
            } catch (_: Throwable) {
                false
            }
        }
    }
}
