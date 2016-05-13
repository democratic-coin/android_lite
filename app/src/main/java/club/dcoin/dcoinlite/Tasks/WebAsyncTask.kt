package club.dcoin.dcoinlite.Tasks

import android.os.AsyncTask
import android.util.Log
import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Created by faraday on 5/7/16.
 * ${PROJECT}
 */

class WebAsyncRequest(val httpClient: OkHttpClient, val webView: WebView): AsyncTask<String, Int, String>() {

    override fun doInBackground(vararg params: String?): String? {
        try {
            val request = Request.Builder().url(params[0]).build()
            val response = httpClient.newCall(request).execute()

            val gson = Gson()
            val json = gson.fromJson(response.body().string(), JsonObject::class.java)
            val pool = json.get("pool").asString
            return pool
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return "localhost"
    }

    override fun onPostExecute(result: String?) {
        super.onPostExecute(result)
        Log.d("MainActivityFragment", "onCreateView $result")
        webView.loadUrl(result)
    }
}