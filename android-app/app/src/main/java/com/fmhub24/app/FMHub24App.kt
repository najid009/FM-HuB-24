package com.fmhub24.app

import android.app.Application
import com.fmhub24.app.data.AppContainer

class FMHub24App : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
