package com.matrix.launcher.system

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/** Thin wrapper around Android's real widget-hosting APIs. Created with an Activity context. */
class WidgetController(private val context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context, HOST_ID)

    fun start() { try { host.startListening() } catch (e: Exception) { /* widgets are optional */ } }
    fun stop() { try { host.stopListening() } catch (e: Exception) { /* ignore */ } }

    fun providers(): List<AppWidgetProviderInfo> = try { manager.installedProviders } catch (e: Exception) { emptyList() }
    fun allocate(): Int = host.allocateAppWidgetId()
    fun delete(id: Int) { try { host.deleteAppWidgetId(id) } catch (e: Exception) { /* ignore */ } }
    fun info(id: Int): AppWidgetProviderInfo? = try { manager.getAppWidgetInfo(id) } catch (e: Exception) { null }

    fun createView(id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return try { host.createView(context, id, info) } catch (e: Exception) { null }
    }

    companion object { const val HOST_ID = 1024 }
}
