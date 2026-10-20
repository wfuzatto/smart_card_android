package br.com.hotelnfcwalletlab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Signing and service-account credentials remain exclusively on the server. */
object Backend {
    suspend fun request(baseUrl: String, apiKey: String, method: String,
                        path: String, data: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val endpoint = baseUrl.trim().trimEnd('/')
            require(endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
                "Informe a URL do backend"
            }
            require(apiKey.length >= 20) { "Informe a chave de acesso do laboratório" }
            val conn = (URL(endpoint + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("X-Lab-Key", apiKey)
                setRequestProperty("Accept", "application/json")
                if (data != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
                }
            }
            try {
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) throw IllegalStateException("HTTP $code: $body")
                JSONObject(body)
            } finally {
                conn.disconnect()
            }
        }
}
