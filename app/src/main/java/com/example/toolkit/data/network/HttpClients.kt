package com.example.toolkit.data.network

import com.example.toolkit.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object HttpClients {
    // Only log request/response headers in debug builds — never leak them to
    // logcat in a shipped release.
    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
        else HttpLoggingInterceptor.Level.NONE
    }

    val default: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(logging)
        .build()

    val noRedirect: OkHttpClient = default.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
}
