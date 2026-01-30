package com.coulterpeterson.floatnative.cast

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.cast.tv.CastReceiverContext

/**
 * Manages CastReceiverContext lifecycle.
 * Start the receiver when activity is visible, stop when backgrounded.
 */
class CastReceiverLifecycleObserver : DefaultLifecycleObserver {
    
    override fun onStart(owner: LifecycleOwner) {
        try {
            CastReceiverContext.getInstance().start()
            Log.d("CastReceiver", "CastReceiverContext started")
        } catch (e: Exception) {
            Log.e("CastReceiver", "Failed to start CastReceiverContext", e)
        }
    }
    
    override fun onStop(owner: LifecycleOwner) {
        try {
            CastReceiverContext.getInstance().stop()
            Log.d("CastReceiver", "CastReceiverContext stopped")
        } catch (e: Exception) {
            Log.e("CastReceiver", "Failed to stop CastReceiverContext", e)
        }
    }
}
