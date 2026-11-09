package com.cronoszip.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A dashboard tile's request for the Files screen: a category name, or a folder path. */
data class BrowseRequest(val category: String? = null, val folder: String? = null)

/** Hands one request from the dashboard to the Files screen, which clears it once applied. */
object BrowseRequests {
    var pending: BrowseRequest? by mutableStateOf(null)
}
