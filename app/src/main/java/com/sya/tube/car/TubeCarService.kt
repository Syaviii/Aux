package com.sya.tube.car

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sya.tube.legacy.SearchCallback
import com.sya.tube.legacy.SearchItem
import com.thekirankumar.youtubeauto2.activity.MainCarActivity
import com.thekirankumar.youtubeauto2.BuildConfig
import com.thekirankumar.youtubeauto2.R
import java.lang.ref.WeakReference

// Personal development integration, retaining CarStream's real Android views.
class TubeCarService : CarAppService() {
    override fun createHostValidator(): HostValidator = if(BuildConfig.DEBUG) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(this).addAllowedHosts(R.array.car_hosts_allowlist).build()
    override fun onCreateSession(): Session = TubeSession()
}

class TubeSession : Session() {
    private val renderer = CarSurfaceRenderer(this)
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer) }
            override fun onResume(owner: LifecycleOwner) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
                },300)
            }
            override fun onDestroy(owner: LifecycleOwner) { renderer.release(); CarBridge.detach(this@TubeSession) }
        })
    }
    override fun onCreateScreen(intent: Intent): Screen {
        CarBridge.attach(this)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
        return object : Screen(carContext) {
            override fun onGetTemplate(): Template = NavigationTemplate.Builder()
                .setActionStrip(ActionStrip.Builder().addAction(Action.Builder().setTitle("Search").setOnClickListener { renderer.showSearch() }.build()).build())
                .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build()).build()
        }
    }
}

object CarBridge {
    private var session = WeakReference<TubeSession>(null)
    private var search: TubeSearchScreen? = null
    fun attach(value: TubeSession) { session = WeakReference(value) }
    fun detach(value: TubeSession) { if(session.get() === value) { session.clear(); search=null } }
    @JvmStatic fun showSearch(hint: String, callback: SearchCallback?): Boolean {
        val context=session.get()?.carContext ?: return false
        if(search!=null) return true
        val screen=TubeSearchScreen(context,hint,callback) { search=null; callback?.onSearchClosed() }
        search=screen; context.getCarService(ScreenManager::class.java).push(screen); return true
    }
    @JvmStatic fun updateSearchHint(hint: String) { search?.let { it.hint=hint; it.invalidate() } }
    @JvmStatic fun updateSearchItems(items: List<SearchItem>) { search?.let { it.items=items; it.invalidate() } }
    @JvmStatic fun hideSearch() {
        search?.let { screen ->
            search=null
            val manager=screen.carContext.getCarService(ScreenManager::class.java)
            if(manager.top === screen) manager.pop()
        }
    }
    @JvmStatic fun isSearchVisible() = search != null
}

private class TubeSearchScreen(context: CarContext, var hint: String, private val callback: SearchCallback?, closed: ()->Unit) : Screen(context) {
    var items: List<SearchItem> = emptyList()
    private var text=""
    private var submitted=false
    private fun submit(value: String) {
        val query=value.trim()
        if(query.isEmpty() || submitted) return
        submitted=true
        if(callback?.onSearchSubmitted(query) != true) submitted=false
    }
    init { lifecycle.addObserver(object : DefaultLifecycleObserver { override fun onDestroy(owner: LifecycleOwner) { closed() } }) }
    override fun onGetTemplate(): Template {
        val list=ItemList.Builder()
        if(text.isNotBlank()) list.addItem(Row.Builder().setTitle("Search: $text").setOnClickListener { submit(text) }.build())
        items.take(if(text.isBlank()) 6 else 5).forEach { item -> list.addItem(Row.Builder().setTitle(item.title).setOnClickListener { submit(item.title.toString()) }.build()) }
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(value: String) { text=value; callback?.onSearchTextChanged(value); invalidate() }
            override fun onSearchSubmitted(value: String) { submit(value) }
        }).setHeaderAction(Action.BACK).setSearchHint(hint).setShowKeyboardByDefault(true).setItemList(list.build()).build()
    }
}

private class CarSurfaceRenderer(private val session: TubeSession) : SurfaceCallback {
    private var display: VirtualDisplay?=null
    private var presentation: CarPresentation?=null
    private var mediaService: android.content.ServiceConnection?=null
    private var visible=Rect()
    private var stable=Rect()
    private var width=0
    private var height=0
    override fun onSurfaceAvailable(container: SurfaceContainer) {
        if(BuildConfig.DEBUG) android.util.Log.d("TubeSurface","available ${container.width}x${container.height}")
        val surface=container.surface ?: return
        width=container.width; height=container.height
        val active=display
        if(active!=null) { active.resize(width,height,container.dpi); active.surface=surface; applyArea(); return }
        val manager=session.carContext.getSystemService(DisplayManager::class.java)
        val created=manager.createVirtualDisplay("TubeCarStream",width,height,container.dpi,surface,
            0)
        display=created
        val shown=CarPresentation(session.carContext,created.display)
        presentation=shown; shown.show(); applyArea()
        if(mediaService==null) mediaService=MediaServiceStarter.start(session.carContext)
    }
    override fun onSurfaceDestroyed(container: SurfaceContainer) { display?.surface=null }
    override fun onVisibleAreaChanged(area: Rect) {
        if(BuildConfig.DEBUG) android.util.Log.d("TubeSurface","visible $area")
        if(stable.isEmpty) { visible=Rect(area); applyArea() }
    }
    override fun onStableAreaChanged(area: Rect) {
        if(BuildConfig.DEBUG) android.util.Log.d("TubeSurface","stable $area")
        stable=Rect(area); visible=Rect(area); applyArea()
    }
    private fun applyArea() {
        val window=presentation?.window ?: return
        // The host can switch between dashboard and full width without recreating
        // the virtual display. Give the Presentation the new bounds as well.
        window.setLayout(width,height)
        val view=window.decorView
        view.setPadding(visible.left.coerceAtLeast(0),visible.top.coerceAtLeast(0),(width-visible.right).coerceAtLeast(0),(height-visible.bottom).coerceAtLeast(0))
    }
    override fun onClick(x: Float,y: Float) {
        dispatch(MotionEvent.ACTION_DOWN,x,y); dispatch(MotionEvent.ACTION_UP,x,y)
    }
    override fun onScroll(distanceX: Float,distanceY: Float) {
        val x=width/2f; val y=height/2f
        dispatch(MotionEvent.ACTION_DOWN,x,y)
        for(step in 1..5) dispatch(MotionEvent.ACTION_MOVE,x-distanceX*step/5,y-distanceY*step/5)
        dispatch(MotionEvent.ACTION_UP,x-distanceX,y-distanceY)
    }
    override fun onFling(velocityX: Float,velocityY: Float) {
        presentation?.findViewById<android.webkit.WebView>(com.thekirankumar.youtubeauto2.R.id.web_view)?.flingScroll(-velocityX.toInt(),-velocityY.toInt())
    }
    override fun onScale(focusX: Float,focusY: Float,scaleFactor: Float) {
        presentation?.findViewById<android.webkit.WebView>(com.thekirankumar.youtubeauto2.R.id.web_view)?.zoomBy(scaleFactor)
    }
    private var down=0L
    private fun dispatch(action: Int,x: Float,y: Float) {
        val shown=presentation ?: return
        val now=SystemClock.uptimeMillis(); if(action==MotionEvent.ACTION_DOWN) down=now
        val event=MotionEvent.obtain(down,now,action,x,y,0); event.source=InputDevice.SOURCE_TOUCHSCREEN
        shown.dispatchTouchEvent(event); event.recycle()
    }
    fun release() {
        presentation?.destroyHost(); presentation=null
        display?.release(); display=null
        MediaServiceStarter.stop(session.carContext,mediaService); mediaService=null
    }
    fun showSearch() { presentation?.getSearchController()?.showSearchBox() }
}
