package com.sya.tube.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.thekirankumar.youtubeauto2.service.MyMediaBrowserService

/**
 * Android Auto binds the projection service with BIND_WAIVE_PRIORITY, so on some phones Tube is still a
 * background app when the car screen is created and startService throws. Binding is allowed in the
 * background and keeps the media session service alive for as long as the car screen holds it
 */
object MediaServiceStarter {
    private val keepAlive = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    @JvmStatic
    fun start(context: Context): ServiceConnection? {
        val intent = Intent(context, MyMediaBrowserService::class.java)
        try { context.startService(intent) } catch (background: IllegalStateException) {}
        // A started service is stopped about a minute after Tube goes to the background, which is exactly when sound keeps
        // playing behind Maps, the binding keeps the media session for as long as the car screen exists
        return if (context.bindService(intent, keepAlive, Context.BIND_AUTO_CREATE)) keepAlive else null
    }

    @JvmStatic
    fun stop(context: Context, connection: ServiceConnection?) {
        if (connection != null) try { context.unbindService(connection) } catch (ignored: IllegalArgumentException) {}
    }
}
