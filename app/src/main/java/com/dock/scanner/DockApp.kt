package com.dock.scanner

import android.app.Application
import android.util.Log
import com.dock.scanner.core.PageProcessor
import com.dock.scanner.data.storage.DockLibrary

class DockApp : Application() {

    val library: DockLibrary by lazy { DockLibrary(this) }

    override fun onCreate() {
        super.onCreate()
        if (!PageProcessor.initOpenCv()) Log.e("DockApp", "OpenCV native library failed to load")
    }
}
