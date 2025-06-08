package main.kotlin

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.http.*

fun main(args: Array<String>) {
    println("Starting server with args: ${args.joinToString { ", " }}")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 3000
    println("Using port: $port")
    
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        routing {
            get("/") {
                call.respondText("Hello, World!")
            }
            // Add more routes here as needed
        }
    }.start(wait = true)
}
