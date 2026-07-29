package com.lagradost.server.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.TvType
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText

val jsonMapper: ObjectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

suspend fun ApplicationCall.respondJson(message: Any) {
    respondText(jsonMapper.writeValueAsString(message), ContentType.Application.Json)
}

fun typeToDisplayName(type: TvType?): String? {
    return when (type) {
        TvType.Movie, TvType.Torrent -> "Movie"
        TvType.AnimeMovie -> "Anime Movie"
        TvType.TvSeries -> "TV Series"
        TvType.Anime -> "Anime"
        TvType.Cartoon -> "Cartoon"
        TvType.OVA -> "OVA"
        TvType.AsianDrama -> "Asian Drama"
        TvType.Live -> "Live"
        TvType.NSFW -> "NSFW"
        TvType.Documentary -> "Documentary"
        TvType.Music -> "Music"
        TvType.AudioBook -> "Audiobook"
        TvType.Audio -> "Audio"
        TvType.Podcast -> "Podcast"
        TvType.Others -> "Other"
        TvType.CustomMedia -> "Custom"
        else -> type?.name
    }
}
