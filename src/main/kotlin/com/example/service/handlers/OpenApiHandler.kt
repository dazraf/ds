package com.example.service.handlers

import com.example.service.openapi.ApiSpecification
import io.vertx.ext.web.RoutingContext

/**
 * Handler for serving OpenAPI specification
 */
class OpenApiHandler {

    private val openApiJson: String by lazy {
        ApiSpecification.toJson()
    }

    fun handle(context: RoutingContext) {
        context.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(openApiJson)
    }
}
