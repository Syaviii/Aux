package com.sya.tube.video.car

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.fragment.app.FragmentController
import androidx.fragment.app.FragmentHostCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.google.android.apps.auto.sdk.CarUiController
import com.google.android.apps.auto.sdk.SupportLibViewLoader
import com.google.android.apps.auto.sdk.ad
import com.sya.tube.legacy.CarUiHost
import com.sya.tube.legacy.SearchController
import com.thekirankumar.youtubeauto2.R
import com.thekirankumar.youtubeauto2.activity.MainCarActivity
import com.thekirankumar.youtubeauto2.fragments.WebViewCarFragment

// Keep the original projection transport and host keyboard, with modern Fragment ownership.
// The SDK's old FragmentActivity implementation cannot drive current AndroidX lifecycles.
class TubeProjectionActivity : com.google.android.gms.car.c(), CarUiHost {
    private val callbacks = mutableSetOf<MainCarActivity.ActivityCallbacks>()
    private lateinit var ui: CarUiController
    private lateinit var sdkContext: ad
    private lateinit var search: SearchController
    private lateinit var host: Host
    private lateinit var controller: FragmentController
    private val viewLoader = SupportLibViewLoader()
    private var consumeVideoTouch = false
    private var mediaService: android.content.ServiceConnection? = null
    private var backConsumed = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        sdkContext = ad(baseContext)
        ui = CarUiController(sdkContext, a(), viewLoader, null)
        super.setContentView(ui.d())
        ui.getStatusBarController().hideAppHeader()
        ui.getStatusBarController().hideConnectivityLevel()
        val content = findViewById(ui.c()) as android.view.ViewGroup
        layoutInflater.inflate(R.layout.activity_car_main, content, true)
        if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) content.post {
            android.util.Log.i("TubeRendering", "hardware=${content.isHardwareAccelerated} flags=${c().attributes.flags} requested=${c().attributes.preferredRefreshRate} display=${content.display?.refreshRate}")
        }
        search = SearchController(this, ui.getSearchController(), a())
        host = Host()
        controller = FragmentController.createController(host)
        host.saved.performAttach()
        host.saved.performRestore(null)
        controller.attachHost(null)
        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        controller.dispatchCreate()
        val fragment = WebViewCarFragment().apply { setCarHost(this@TubeProjectionActivity); setBackHandledByHost(true) }
        controller.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).commitNow()
        controller.dispatchActivityCreated()
        c().decorView.systemUiVisibility = 6150
        val previous = c().callback
        c().callback = object : Window.Callback by previous {
            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                // Keys must reach the host, Android Auto counts a key the host never sees as an ANR and kills the app
                val handled = previous.dispatchKeyEvent(event)
                if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubeControls", "Key ${event.keyCode} action=${event.action} handled=$handled focus=${c().currentFocus?.javaClass?.simpleName}")
                return handled
            }
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG && event.action == MotionEvent.ACTION_UP && event.y < 120) android.util.Log.i("TubeControls", "Touch ${event.x},${event.y}")
                if (event.action == MotionEvent.ACTION_DOWN) {
                    consumeVideoTouch = (controller.supportFragmentManager.findFragmentById(R.id.fragment_container) as? WebViewCarFragment)?.onCarSurfaceTouch(event.x, event.y) == true
                }
                if (consumeVideoTouch) {
                    if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) consumeVideoTouch = false
                    return true
                }
                return previous.dispatchTouchEvent(event)
            }
        }
        mediaService = com.sya.tube.car.MediaServiceStarter.start(this)
    }
    override fun onStart() { super.onStart(); idleHandler.removeCallbacks(closeWhenIdle); ui.a(); host.registry.handleLifecycleEvent(Lifecycle.Event.ON_START); controller.dispatchStart(); readCarFrameRate() }

    /** The SDK keeps the car host in a private field, it answers things the activity base class does not expose */
    private fun callHost(name: String): Any? {
        try {
            var type: Class<*>? = com.google.android.gms.car.c::class.java
            while (type != null) {
                for (field in type.declaredFields) {
                    field.isAccessible = true
                    val value = field.get(this) ?: continue
                    val method = value.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 } ?: continue
                    return method.invoke(value) ?: Unit
                }
                type = type.superclass
            }
        } catch (error: Throwable) {
            com.sya.tube.diag.Diag.log("car", "host $name failed ${error.javaClass.simpleName}")
        }
        return null
    }

    /** The frame rate callback does not always arrive, the host also answers directly */
    private fun readCarFrameRate() {
        val rate = callHost("getMaxFrameRate") as? Int ?: return
        if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubeNativeMetrics", "stage carMaxFrameRate=$rate")
        onFrameRateChange(rate)
    }

    // While the car screen exists the phone shows a screen cast icon and keeps the page and player alive, after a minute
    // out of view Tube closes it, the video and position are saved first so opening Tube again continues there
    private val idleHandler = Handler(Looper.getMainLooper())
    private val closeWhenIdle: Runnable = Runnable {
        val fragment = carFragment()
        // Sound still playing behind another app keeps the car screen, it closes a minute after that stops
        if (fragment?.isPlayingInBackground() == true) { idleHandler.postDelayed(closeWhenIdle, 60_000); return@Runnable }
        com.sya.tube.diag.Diag.log("car", "closing car screen, out of view for a minute")
        if (fragment == null) callHost("finish") else fragment.saveResumePoint { callHost("finish") }
    }
    override fun onRestoreInstanceState(state: Bundle) {
        // The host decor includes parcelables loaded from Android Auto, not Tube's APK.
        state.getBundle("android:viewHierarchyState")?.classLoader = sdkContext.a().classLoader
        super.onRestoreInstanceState(state)
        ui.a(state)
    }
    override fun onSaveInstanceState(state: Bundle) { super.onSaveInstanceState(state); ui.b(state) }
    override fun onResume() { super.onResume(); controller.noteStateNotSaved(); host.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME); controller.dispatchResume() }
    override fun onPause() { controller.dispatchPause(); host.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); super.onPause() }
    override fun onStop() { controller.dispatchStop(); host.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP); ui.b(); super.onStop(); idleHandler.postDelayed(closeWhenIdle, 60_000) }
    override fun onDestroy() {
        idleHandler.removeCallbacks(closeWhenIdle)
        search.hideSearchBox()
        controller.dispatchDestroy(); host.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        host.viewModelStore.clear()
        com.sya.tube.car.MediaServiceStarter.stop(this, mediaService); mediaService = null
        super.onDestroy()
    }
    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config); ui.a(config); controller.dispatchConfigurationChanged(config)
        callbacks.toList().forEach { it.onConfigChanged() }
    }
    override fun onWindowFocusChanged(focused: Boolean, touchMode: Boolean) {
        super.onWindowFocusChanged(focused, touchMode)
        callbacks.toList().forEach { it.onWindowFocusChanged(focused) }
    }
    override fun onFrameRateChange(rate: Int) {
        super.onFrameRateChange(rate)
        if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubeNativeMetrics", "stage carFrameRate=$rate")
        // The projection protocol codes 30 fps as 1 and 60 fps as 2, a plain frame rate is taken as is
        val fps = when (rate) { 1 -> 30; 2 -> 60; else -> rate }
        com.sya.tube.diag.Diag.log("car", "frame rate $rate -> $fps")
        if (fps >= 15) com.sya.tube.player.YoutubeStreams.carFrameRate = fps
    }
    override fun onKeyDown(code: Int, event: android.view.KeyEvent): Boolean {
        val handled = super.onKeyDown(code, event)
        if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubeControls", "host onKeyDown $code -> $handled")
        return handled
    }
    override fun onKeyUp(code: Int, event: android.view.KeyEvent): Boolean {
        val handled = super.onKeyUp(code, event)
        if (com.thekirankumar.youtubeauto2.BuildConfig.DEBUG) android.util.Log.i("TubeControls", "host onKeyUp $code -> $handled")
        return handled
    }
    override fun onBackPressed() {
        // The host routes back here from onKeyUp, the fragment leaves fullscreen or goes back a page
        if (carFragment()?.goBack() == true) return
        super.onBackPressed()
    }
    private fun carFragment() = controller.supportFragmentManager.findFragmentById(R.id.fragment_container) as? WebViewCarFragment
    override fun getSearchController() = search
    override fun getCarInputController() = search
    override fun getCarWindow(): Window = c()
    override fun addActivityCallback(callback: MainCarActivity.ActivityCallbacks) { callbacks.add(callback) }
    override fun removeActivityCallback(callback: MainCarActivity.ActivityCallbacks) { callbacks.remove(callback) }

    private inner class Host : FragmentHostCallback<TubeProjectionActivity>(this@TubeProjectionActivity, Handler(Looper.getMainLooper()), 0), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
        val registry = LifecycleRegistry(this)
        val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val onBackPressedDispatcher = OnBackPressedDispatcher()
        override fun onGetHost() = this@TubeProjectionActivity
        override fun onGetLayoutInflater(): LayoutInflater = this@TubeProjectionActivity.layoutInflater.cloneInContext(this@TubeProjectionActivity)
        override fun onFindViewById(id: Int): View? = this@TubeProjectionActivity.findViewById(id)
        override fun onHasView() = true
    }
}
