package com.sya.tube.car

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.MotionEvent
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
import com.sya.tube.legacy.CarUiHost
import com.sya.tube.legacy.SearchController
import com.thekirankumar.youtubeauto2.R
import com.thekirankumar.youtubeauto2.activity.MainCarActivity
import com.thekirankumar.youtubeauto2.fragments.WebViewCarFragment

// Hosts the original Fragments on a Presentation: no Activity launch on an untrusted display.
class CarPresentation(context: Context, display: Display) : Presentation(context, display, R.style.Theme_AppCompat), CarUiHost {
    private val callbacks=mutableSetOf<MainCarActivity.ActivityCallbacks>()
    private val search=SearchController(this.context)
    private val host=Host()
    private val controller=FragmentController.createController(host)
    private var destroyed=false
    private var consumeVideoTouch=false
    override fun getSearchController()=search
    override fun getCarInputController()=search
    override fun getCarWindow(): Window=checkNotNull(window)
    override fun addActivityCallback(callback: MainCarActivity.ActivityCallbacks) { callbacks.add(callback) }
    override fun removeActivityCallback(callback: MainCarActivity.ActivityCallbacks) { callbacks.remove(callback) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        host.saved.performAttach(); host.saved.performRestore(null)
        controller.attachHost(null)
        setContentView(R.layout.activity_car_main)
        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        controller.dispatchCreate()
        val fragment=WebViewCarFragment().apply { setCarHost(this@CarPresentation) }
        controller.supportFragmentManager.beginTransaction().replace(R.id.fragment_container,fragment).commitNow()
        controller.dispatchActivityCreated()
        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_START); controller.dispatchStart()
        host.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME); controller.dispatchResume()
    }
    override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused)
        callbacks.toList().forEach { it.onWindowFocusChanged(focused) }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if(event.action==MotionEvent.ACTION_DOWN) {
            consumeVideoTouch=(controller.supportFragmentManager.findFragmentById(R.id.fragment_container) as? WebViewCarFragment)?.onCarSurfaceTouch(event.x,event.y) == true
        }
        if(consumeVideoTouch) {
            if(event.action==MotionEvent.ACTION_UP || event.action==MotionEvent.ACTION_CANCEL) consumeVideoTouch=false
            return true
        }
        return super.dispatchTouchEvent(event)
    }
    fun destroyHost() {
        if(destroyed) return
        destroyed=true
        search.hideSearchBox()
        controller.dispatchPause(); host.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        controller.dispatchStop(); host.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        controller.dispatchDestroy(); host.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        host.viewModelStore.clear()
        dismiss()
    }
    private inner class Host : FragmentHostCallback<CarPresentation>(this@CarPresentation.context,Handler(Looper.getMainLooper()),0), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
        val registry=LifecycleRegistry(this)
        val saved=SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get()=registry
        override val viewModelStore=ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry get()=saved.savedStateRegistry
        override val onBackPressedDispatcher=OnBackPressedDispatcher()
        override fun onGetHost()=this@CarPresentation
        override fun onGetLayoutInflater(): LayoutInflater=LayoutInflater.from(this@CarPresentation.context).cloneInContext(this@CarPresentation.context)
        override fun onFindViewById(id: Int): View?=this@CarPresentation.findViewById(id)
        override fun onHasView()=true
    }
}
