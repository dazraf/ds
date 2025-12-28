package com.example.service.handlers

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext

/**
 * Handler for health check endpoint.
 *
 * Returns the current health status of the service as a JSON response
 * with HTTP 200 status code and a simple "OK" status indicator.
 */
class HealthHandler {

    fun handle(context: RoutingContext) {
        val response = JsonObject()
            .put("status", "OK")

        context.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(response.encode())
    }
}
