@file:OpenAPIDefinition(
    info = Info(
        title = "Secure Upload",
        version = "1.0.0",
        description = "Secure client-side encrypted upload service",
    ),
    servers = [Server(url = "http://localhost:8080", description = "Local development server")]
)

package app

import io.micronaut.runtime.Micronaut
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.servers.Server

fun main(args: Array<String>) {
    Micronaut.build()
        .args(*args)
        .packages("app")
        .start()
}
