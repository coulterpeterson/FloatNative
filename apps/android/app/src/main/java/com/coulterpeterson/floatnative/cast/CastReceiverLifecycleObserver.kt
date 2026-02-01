package com.coulterpeterson.floatnative.cast

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.cast.tv.CastReceiverContext

/**
 * Manages CastReceiverContext lifecycle.
 * NOTE: CastReceiverContext.start() is called synchronously in TvMainActivity.onCreate()
 * to ensure proper initialization order. This observer only handles stopping the context
 * when the activity is backgrounded.
 */
class CastReceiverLifecycleObserver : DefaultLifecycleObserver {
    
    override fun onStop(owner: LifecycleOwner) {
        try {
            CastReceiverContext.getInstance().stop()
            Log.d("CastReceiver", "CastReceiverContext stopped")
        } catch (e: Exception) {
            Log.e("CastReceiver", "Failed to stop CastReceiverContext", e)
        }
    }
}
