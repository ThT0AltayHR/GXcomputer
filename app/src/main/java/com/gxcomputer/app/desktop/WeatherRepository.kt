package com.gxcomputer.app.desktop

import android.content.Context
import android.location.LocationManager
import com.gxcomputer.app.R
import com.gxcomputer.app.core.Constants
import com.gxcomputer.app.core.PreferencesManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class WeatherResult(val displayText: String, val iconRes: Int)

private data class OpenWeatherResponse(
    val weather: List<WeatherEntry>?,
    val main: MainEntry?,
    val name: String?
)
private data class WeatherEntry(val icon: String?, val description: String?)
private data class MainEntry(val temp: Double?)

private interface OpenWeatherApi {
    @GET("data/2.5/weather")
    fun current(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "tr"
    ): Call<OpenWeatherResponse>
}

/**
 * OpenWeatherMap üzerinden GERÇEK, canlı hava durumu verisi çeker - konuma göre.
 * Constants.OPENWEATHER_API_KEY boşsa veri UYDURULMAZ; arayüz "servis ayarlanmadı"
 * durumunu gösterir (bkz. README.md -> kendi ücretsiz anahtarınızı ekleme adımları).
 * Her çağrıda cihazın O ANKİ konumu kullanıldığı için, konum değiştiğinde
 * otomatik olarak farklı/güncel hava durumu gösterilmiş olur.
 */
class WeatherRepository(private val context: Context) {

    private val prefs = PreferencesManager(context)

    private val api: OpenWeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWeatherApi::class.java)
    }

    fun fetchForCurrentLocation(
        onResult: (WeatherResult) -> Unit,
        onNoApiKey: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (Constants.OPENWEATHER_API_KEY.isBlank()) {
            onNoApiKey()
            return
        }

        val location = lastKnownLocation()
        if (location == null) {
            onError(IllegalStateException("Konum alınamadı"))
            return
        }
        val (lat, lon) = location
        prefs.saveLastLocation(lat, lon)

        api.current(lat, lon, Constants.OPENWEATHER_API_KEY).enqueue(object : Callback<OpenWeatherResponse> {
            override fun onResponse(call: Call<OpenWeatherResponse>, response: Response<OpenWeatherResponse>) {
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    onError(IllegalStateException("HTTP ${response.code()}")); return
                }
                val temp = body.main?.temp?.let { Math.round(it) } ?: run { onError(IllegalStateException("Veri yok")); return }
                val iconCode = body.weather?.firstOrNull()?.icon ?: "01d"
                onResult(WeatherResult("$temp°C · ${body.name ?: ""}", iconForCode(iconCode)))
            }

            override fun onFailure(call: Call<OpenWeatherResponse>, t: Throwable) {
                onError(Exception(t))
            }
        })
    }

    private fun iconForCode(code: String): Int = when {
        code.startsWith("01") -> R.drawable.ic_weather_sun
        code.startsWith("09") || code.startsWith("10") || code.startsWith("11") -> R.drawable.ic_weather_rain
        else -> R.drawable.ic_weather_cloud
    }

    private fun lastKnownLocation(): Pair<Double, Double>? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            for (provider in providers) {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) return loc.latitude to loc.longitude
            }
            prefs.getLastLocation()
        } catch (e: SecurityException) {
            null
        }
    }
}
