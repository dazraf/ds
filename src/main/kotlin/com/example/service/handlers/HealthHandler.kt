package com.example.service.handlers

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext

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
