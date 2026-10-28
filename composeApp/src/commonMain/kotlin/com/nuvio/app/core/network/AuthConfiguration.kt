package com.nuvio.app.core.network

internal class MissingAuthConfiguration : IllegalStateException(
    "Account sign-in is unavailable in this build: backend configuration is missing."
)

internal fun requireAuthConfiguration(configuration: ServerConfiguration) {
    if (configuration.backendUrl.isBlank() || configuration.publishableKey.isBlank()) {
        throw MissingAuthConfiguration()
    }
}
