package com.matrix.launcher.system

import android.content.Context
import android.content.Intent
import android.provider.Settings

fun openHomeSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: Exception) {
            // nothing else we can do
        }
    }
}
