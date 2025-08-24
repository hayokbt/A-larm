package io.github.arashiyama11.a_larm.infra

import io.github.arashiyama11.a_larm.domain.CalendarReadGateway
import io.github.arashiyama11.a_larm.domain.DayBriefGateway
import io.github.arashiyama11.a_larm.domain.models.DayBrief
import io.github.arashiyama11.a_larm.domain.models.WeatherBrief
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class DayBriefGatewayImpl @Inject constructor(
    private val calendarReadGateway: CalendarReadGateway
) : DayBriefGateway {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    override suspend fun buildBrief(forDate: LocalDate): DayBrief {
        val events = calendarReadGateway.eventsOn(forDate)
        val weather = getWeatherForecast()
        return DayBrief(
            date = LocalDateTime.now(),
            weather = weather!!.toDomain(),
            calendar = events
        )
    }

    private suspend fun getWeatherForecast(): Forecast? {
        val response: ForecastResponse = httpClient.get(ENDPOINT).body()
        return response.forecasts.firstOrNull { it.dateLabel == "今日" }
    }

    companion object {
        private const val ENDPOINT = "https://weather.tsukumijima.net/api/forecast/city/130010"
    }
}

@Serializable
private data class ForecastResponse(
    val forecasts: List<Forecast>
)

@Serializable
private data class Forecast(
    val date: String,
    val dateLabel: String,
    val detail: JsonObject,
    val temperature: JsonObject
) {
    fun toDomain(): WeatherBrief {
        return WeatherBrief(
            summary = detail.toString() + temperature.toString()
        )
    }
}