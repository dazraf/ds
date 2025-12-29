package com.example.service.database

import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.PoolOptions

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int = 10
) {
    fun toPgConnectOptions(): PgConnectOptions {
        return PgConnectOptions()
            .setHost(host)
            .setPort(port)
            .setDatabase(database)
            .setUser(user)
            .setPassword(password)
    }

    fun toPoolOptions(): PoolOptions {
        return PoolOptions().setMaxSize(maxPoolSize)
    }

    fun toJdbcUrl(): String {
        return "jdbc:postgresql://$host:$port/$database"
    }

    companion object {
        fun fromJsonObject(json: io.vertx.core.json.JsonObject): DatabaseConfig {
            return DatabaseConfig(
                host = json.getString("host"),
                port = json.getInteger("port"),
                database = json.getString("database"),
                user = json.getString("user"),
                password = json.getString("password"),
                maxPoolSize = json.getInteger("maxPoolSize", 10)
            )
        }
    }
}
