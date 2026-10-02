package com.bahar.calltracker

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    const val WEB_URL = "https://script.google.com/macros/s/AKfycbzj1TV1bW0AlBkerRm8WNvVgf6RePriRJmxE8qQ8MOd2XMwJi9zfGU3GP32kps3Sp5EFw/exec"

    fun post(o: JSONObject): JSONObject {
        val c = URL(WEB_URL).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("Content-Type", "text/plain")
        c.outputStream.use { it.write(o.toString().toByteArray()) }
        return JSONObject(c.inputStream.bufferedReader().readText())
    }
}
