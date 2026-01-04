package com.homeplanner.api

import okhttp3.OkHttpClient

/**
 * Основной API для работы с сервером.
 * Комбинирует функциональность ServerTaskApi и ServerSyncApi.
 */
typealias ServerApi = ServerSyncApi

// Если нужно, можно создать фабрику или что-то, но typealias достаточно.