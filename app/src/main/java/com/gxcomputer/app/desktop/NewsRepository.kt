package com.gxcomputer.app.desktop

import android.content.Context
import com.gxcomputer.app.core.Constants
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class NewsItem(val title: String, val source: String)

private data class NewsApiResponse(val articles: List<NewsArticle>?)
private data class NewsArticle(val title: String?, val source: NewsSource?)
private data class NewsSource(val name: String?)

private interface NewsApi {
    @GET("v2/top-headlines")
    fun topHeadlines(
        @Query("country") country: String,
        @Query("apiKey") apiKey: String,
        @Query("pageSize") pageSize: Int = 10
    ): Call<NewsApiResponse>
}

/**
 * NewsAPI.org üzerinden GERÇEK gündem başlıkları çeker.
 * Constants.NEWSAPI_API_KEY boşsa sahte/uydurma başlık GÖSTERİLMEZ;
 * panel "servis ayarlanmadı" durumunu net biçimde belirtir.
 */
class NewsRepository(private val context: Context) {

    private val api: NewsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://newsapi.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NewsApi::class.java)
    }

    fun fetchTopHeadlines(
        onResult: (List<NewsItem>) -> Unit,
        onNoApiKey: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (Constants.NEWSAPI_API_KEY.isBlank()) {
            onNoApiKey()
            return
        }

        api.topHeadlines(Constants.NEWSAPI_COUNTRY, Constants.NEWSAPI_API_KEY)
            .enqueue(object : Callback<NewsApiResponse> {
                override fun onResponse(call: Call<NewsApiResponse>, response: Response<NewsApiResponse>) {
                    val articles = response.body()?.articles
                    if (!response.isSuccessful || articles == null) {
                        onError(IllegalStateException("HTTP ${response.code()}")); return
                    }
                    onResult(articles.mapNotNull { a ->
                        val title = a.title ?: return@mapNotNull null
                        NewsItem(title, a.source?.name ?: "")
                    })
                }

                override fun onFailure(call: Call<NewsApiResponse>, t: Throwable) {
                    onError(Exception(t))
                }
            })
    }
}
