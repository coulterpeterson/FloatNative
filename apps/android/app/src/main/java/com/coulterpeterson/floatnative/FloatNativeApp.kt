package com.coulterpeterson.floatnative

import android.app.Application
import com.coulterpeterson.floatnative.api.FloatplaneApi

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger

class FloatNativeApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize API Singleton
        FloatplaneApi.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // Ignored if maxSize is set? Let's use fixed size to match iOS 200MB
                    .maxSizeBytes(200L * 1024 * 1024) // 200MB
                    .build()
            }
            .crossfade(true)
            .logger(DebugLogger())
            .build()
    }
}
