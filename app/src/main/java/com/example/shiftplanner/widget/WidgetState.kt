package com.example.shiftplanner.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.state.GlanceStateDefinition
import java.io.File

// Extension property to provide a singleton DataStore instance for widgets
val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_state_store")

object WidgetStateDefinition : GlanceStateDefinition<Preferences> {

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<Preferences> {
        return context.widgetDataStore
    }

    override fun getLocation(context: Context, fileKey: String): File {
        return File(context.filesDir, "datastore/$fileKey")
    }
}