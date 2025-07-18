package com.gboard.nsfw.utils

import android.net.Uri

object UriUtils {
    fun removeUriParameters(uri: Uri, keysToRemove: List<String?>): Uri.Builder {
        val builder = uri.buildUpon()
        val queryParameterNames = uri.getQueryParameterNames()
        val newUri = builder.clearQuery()
        for (paramName in queryParameterNames) {
            if (!keysToRemove.contains(paramName)) {
                val paramValues = uri.getQueryParameters(paramName)
                for (paramValue in paramValues) {
                    newUri.appendQueryParameter(paramName, paramValue)
                }
            }
        }
        return newUri
    }

    // 🔧 Usage Example for v15.5:
    // val keysToRemove = listOf("safe", "client", "gs_ri", "ei")
}
