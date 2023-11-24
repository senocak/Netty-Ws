package com.github.senocak.nettyws

import com.corundumstudio.socketio.AckRequest
import com.corundumstudio.socketio.Configuration
import com.corundumstudio.socketio.SocketIOClient
import com.corundumstudio.socketio.SocketIONamespace
import com.corundumstudio.socketio.SocketIOServer
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.util.function.Consumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@SpringBootApplication
class NettywsApplication {
    private val log: Logger = LoggerFactory.getLogger(this.javaClass)

    @Value("\${ws.server.host:localhost}")
    private lateinit var host: String

    @Value("\${ws.server.port:9094}")
    private lateinit var port: String

    private lateinit var server: SocketIOServer

    @PostConstruct
    fun setup() =
        SocketIOServer(Configuration().also { it.hostname = host; it.port = port.toInt() })
            .also {
                it.addConnectListener { client: SocketIOClient -> log.info("new user connected with socket. ${client.sessionId}") }
                it.addDisconnectListener { client: SocketIOClient ->
                    client.namespace.allClients.forEach(
                        Consumer { data: SocketIOClient -> log.info("user disconnected. ${data.sessionId}") }
                    )
                }
                val namespace: SocketIONamespace = it.addNamespace("/chat")
                namespace.addConnectListener { client: SocketIOClient ->
                    log.info("Client[${client.sessionId}] - Connected to chat module through '${client.handshakeData.url}'")
                }
                namespace.addDisconnectListener { client: SocketIOClient ->
                    log.info("Client[${client.sessionId}] - Disconnected from chat module.")
                }
                namespace.addEventListener("chat", ChatMessage::class.java) {
                        client: SocketIOClient, data: ChatMessage, _: AckRequest? ->
                    log.info("Client[${client.sessionId}] - Received chat message '$data'")
                    namespace.broadcastOperations.sendEvent("chat", data)
                }
                server = it
                it.start()
            }

    @GetMapping("/ping")
    fun ping(): String = server.allClients.toString()

    @PreDestroy
    fun destroy() {
        log.info("Destroy")
        server.stop()
    }
}

fun main(args: Array<String>) {
    runApplication<NettywsApplication>(*args)
}

internal class ChatMessage {
    val userName: String? = null
    val message: String? = null
    override fun toString(): String = "ChatMessage(userName=$userName, message=$message)"
}