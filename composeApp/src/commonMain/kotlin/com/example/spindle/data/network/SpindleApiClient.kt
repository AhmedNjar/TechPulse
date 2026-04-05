package com.example.spindle.data.network

import com.example.spindle.data.network.dto.*
import com.example.spindle.domain.models.SimulationMode
import com.example.spindle.domain.models.SpindleConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class SpindleApiClient(private val baseUrl: String = "http://127.0.0.1:8000") {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val httpClient = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis  = 120_000
        }
        install(Logging) {
            level = LogLevel.HEADERS
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }

    suspend fun submitSimulation(config: SpindleConfig): SimulateResponseDto {
        val dto = SimulateRequestDto(
            configId            = config.configId,
            projectName         = config.projectName,
            mode                = config.simulationMode.name,
            geometry            = config.geometry,
            bearingSystem       = config.bearingSystem,
            operatingSpeedRpm   = config.operatingSpeedRpm,
            axialLoadN          = config.axialLoadN,
            radialLoadN         = config.radialLoadN,
            operatingTempC      = config.operatingTempC,
            material            = config.material.name,
            stochastic          = config.stochastic,
            enableFEA           = config.enableFEA,
            nModalModes         = config.nModalModes
        )
        return httpClient.post("$baseUrl/api/simulate") { setBody(dto) }.body()
    }

    suspend fun fetchResult(resultId: String): ResultResponseDto? {
        val response = httpClient.get("$baseUrl/api/results/$resultId")
        return if (response.status == HttpStatusCode.Accepted) null else response.body()
    }

    fun streamOptimisation(configId: String): Flow<OptimisationEventDto> = flow {
        httpClient.prepareGet("$baseUrl/api/optimisation/stream") {
            parameter("config_id", configId)
            accept(ContentType.Text.EventStream)
        }.execute { response ->
            val channel: ByteReadChannel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val raw = line.removePrefix("data:").trim()
                if (raw.isEmpty()) continue
                try {
                    val event = json.decodeFromString(OptimisationEventDto.serializer(), raw)
                    emit(event)
                    if (event.eventType == "COMPLETE" || event.eventType == "ERROR") break
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun checkHealth(): HealthDto = try {
        httpClient.get("$baseUrl/api/health").body()
    } catch (_: Exception) {
        HealthDto("unreachable", ansysAvailable = false, pyvistaAvailable = false,
            mlModelLoaded = false, version = "N/A")
    }

    fun close() = httpClient.close()
}

class SimulationApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
