package com.sya.tube.video.car

// CarStream's original projection protocol. This does not register a navigation app.
// Android Auto drops a projection service whose class name another installed app already uses, so this can't share Tube's name
class AuxProjectionService : com.google.android.gms.car.d() {
    override fun getCarActivity(): Class<out com.google.android.gms.car.c> = TubeProjectionActivity::class.java
}
