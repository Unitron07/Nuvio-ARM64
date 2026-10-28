package com.nuvio.app.stage3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.Executors

internal class Stage3Fixture : AutoCloseable {
    data class Request(val method: String, val path: String, val query: String?, val headers: Map<String, String>, val body: String)
    val requests = Collections.synchronizedList(mutableListOf<Request>())
    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val baseUrl get() = "http://127.0.0.1:${server.address.port}"
    @Volatile var providerStatus = 200
    @Volatile var unrestrictStatus = 200
    @Volatile var manifestStatus = 200

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            exchange.use {
                requests += Request(exchange.requestMethod, exchange.requestURI.path, exchange.requestURI.rawQuery,
                    exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(",") },
                    exchange.requestBody.bufferedReader().readText())
                val path = exchange.requestURI.path
                when {
                    path == "/manifest.json" -> reply(exchange, manifestStatus, fixture("manifest.json"))
                    path.startsWith("/catalog/") -> reply(exchange, 200, fixture("catalog.json"))
                    path.startsWith("/meta/") -> reply(exchange, 200, fixture("meta.json"))
                    path.startsWith("/stream/") -> reply(exchange, 200, fixture("stream.json"))
                    path == "/redirect" -> {
                        exchange.responseHeaders.set("Location", "$baseUrl/video?${exchange.requestURI.rawQuery}")
                        reply(exchange, 302, "")
                    }
                    path == "/video" -> reply(exchange, 200, "authorized-fixture-bytes")
                    path == "/user" -> reply(exchange, providerStatus, "{}")
                    path == "/torrents/addMagnet" -> reply(exchange, providerStatus, """{"id":"fixture-torrent"}""")
                    path.startsWith("/torrents/info/") -> reply(exchange, 200, """{"id":"fixture-torrent","status":"downloaded","files":[{"id":1,"path":"/fixture.mp4","bytes":1024,"selected":1}],"links":["$baseUrl/hosted?link=one%2Btwo"]}""")
                    path.startsWith("/torrents/selectFiles/") || path.startsWith("/torrents/delete/") -> reply(exchange, 204, "")
                    path == "/unrestrict/link" -> reply(exchange, unrestrictStatus, """{"download":"$baseUrl/video?fixture=one%2Btwo&session=local","filename":"fixture.mp4","filesize":1024}""")
                    else -> reply(exchange, 503, "controlled failure")
                }
            }
        }
        server.start()
    }

    fun fixture(name: String): String = checkNotNull(javaClass.getResourceAsStream("/stage3/$name"))
        .bufferedReader().use { it.readText() }.replace("BASE_URL", baseUrl)

    private fun reply(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, if (status == 204 || bytes.isEmpty()) -1 else bytes.size.toLong())
        if (status != 204 && bytes.isNotEmpty()) exchange.responseBody.write(bytes)
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }
}
