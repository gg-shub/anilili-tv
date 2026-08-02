package com.shubh.anililitv.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class AniZipClient(
    private val client: OkHttpClient,
    private val json: Json,
) {
data class AniZipMetadata(val logoUrl: String?, val backdropUrl: String?)

    suspend fun getMetadata(anilistId: Int): AniZipMetadata? = withContext(Dispatchers.IO) {
        val url = "https://api.ani.zip/mappings?anilist_id=$anilistId"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
            
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val images = root["images"]?.jsonArray
                val clearLogos = images?.filter {
                    val type = it.jsonObject["coverType"]?.jsonPrimitive?.content?.lowercase()
                    type == "clearlogo" || type == "hdclearart"
                }
                val logoUrl = clearLogos?.firstOrNull { 
                    val lang = it.jsonObject["lang"]?.jsonPrimitive?.content?.lowercase()
                    lang == "en" || lang == "en-us"
                }?.jsonObject?.get("url")?.jsonPrimitive?.content 
                    ?: clearLogos?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content

                val backdropUrl = images?.firstOrNull {
                    val type = it.jsonObject["coverType"]?.jsonPrimitive?.content?.lowercase()
                    type == "showbackground" || type == "fanart"
                }?.jsonObject?.get("url")?.jsonPrimitive?.content
                AniZipMetadata(logoUrl, backdropUrl)
            }
        }.getOrNull()
    }

    suspend fun resolveMapping(anilistId: Int): Int? = withContext(Dispatchers.IO) {
        val url = "https://api.ani.zip/mappings?anilist_id=$anilistId"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
            
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val mappings = root["mappings"]?.jsonObject
                mappings?.get("mal_id")?.jsonPrimitive?.intOrNull
            }
        }.getOrNull()
    }
}
