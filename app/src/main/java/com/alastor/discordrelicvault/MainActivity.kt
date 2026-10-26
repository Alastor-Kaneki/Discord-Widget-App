package com.alastor.discordrelicvault

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val found = linkedMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 16)
            setBackgroundColor(Color.rgb(22, 22, 29))
        }
        val title = TextView(this).apply {
            text = "Discord RelicVault"
            textSize = 26f
            setTextColor(Color.WHITE)
        }
        root.addView(title)
        val status = TextView(this).apply {
            text = "Load a public JSON archive of released Discord collectibles"
            setTextColor(Color.LTGRAY)
        }
        root.addView(status)
        val url = EditText(this).apply {
            hint = "HTTPS catalog JSON URL"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        root.addView(url)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun download(name: String, link: String) {
            try {
                val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val req = DownloadManager.Request(android.net.Uri.parse(link))
                    .setTitle(name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "DiscordRelicVault/" + safeName
                    )
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            } catch (e: Exception) {
                status.text = e.message
            }
        }

        fun walk(value: Any?, output: MutableMap<String, String>, depth: Int = 0) {
            if (depth > 25 || output.size >= 10000) return
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = value.opt(key)
                        if (child is String &&
                            child.startsWith("https://") &&
                            (child.contains("discord.com") || child.contains("discordapp.com")) &&
                            (key.contains("url", true) || key == "src")
                        ) {
                            output["asset_" + output.size] = child
                        }
                        walk(child, output, depth + 1)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until value.length()) walk(value.opt(i), output, depth + 1)
                }
            }
        }

        root.addView(Button(this).apply {
            text = "Load catalog"
            setOnClickListener {
                val link = url.text.toString().trim()
                if (!link.startsWith("https://")) {
                    status.text = "HTTPS required"
                } else {
                    thread {
                        try {
                            val conn = URL(link).openConnection()
                            conn.connectTimeout = 15000
                            conn.readTimeout = 20000
                            val json = conn.getInputStream().bufferedReader().use { it.readText() }
                            val parsed = JSONTokener(json).nextValue()
                            val output = linkedMapOf<String, String>()
                            walk(parsed, output)
                            runOnUiThread {
                                found.clear()
                                found.putAll(output)
                                list.removeAllViews()
                                status.text = "Found " + found.size + " asset URLs"
                                found.forEach { (name, asset) ->
                                    list.addView(Button(this@MainActivity).apply {
                                        text = name
                                        setOnClickListener { download(name, asset) }
                                    })
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread { status.text = "Error: " + e.message }
                        }
                    }
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Download all"
            setOnClickListener {
                found.forEach { (name, link) -> download(name, link) }
                status.text = "Queued " + found.size + " downloads"
            }
        })
        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }
}
