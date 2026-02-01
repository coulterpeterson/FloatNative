package com.coulterpeterson.floatnative.cast

import android.content.Context
import com.google.android.gms.cast.tv.CastReceiverOptions
import com.google.android.gms.cast.tv.ReceiverOptionsProvider

class CastReceiverOptionsProvider : ReceiverOptionsProvider {
    override fun getOptions(context: Context): CastReceiverOptions {
        android.util.Log.d("CastReceiver", "CastReceiverOptionsProvider: getOptions called")
        return CastReceiverOptions.Builder(context)
            .setStatusText("Casting to FloatNative")
            .setVersionCode(1)
            .build()
    }
}
