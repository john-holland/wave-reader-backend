package main.kotlin.graphql

import main.kotlin.config.ComponentConfig
import main.kotlin.config.ComponentConfigInput
import main.kotlin.config.ComponentConfigService
import main.kotlin.logging.DataDogLogger
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import java.util.concurrent.ConcurrentHashMap

class ComponentConfigResolver(
    private val configService: ComponentConfigService
) {
    private val configSubscribers = ConcurrentHashMap<String, FluxSink<ComponentConfig>>()

    fun getComponentConfig(id: String): ComponentConfig? {
        return try {
            configService.getConfig(id)
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to get component config",
                mapOf(
                    "error" to e.message,
                    "id" to id
                )
            )
            null
        }
    }

    fun getComponentConfigsByType(type: String): List<ComponentConfig> {
        return try {
            configService.getConfigsByType(type)
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to get component configs by type",
                mapOf(
                    "error" to e.message,
                    "type" to type
                )
            )
            emptyList()
        }
    }

    fun createComponentConfig(input: ComponentConfigInput): ComponentConfig {
        return try {
            if (!configService.validateConfig(input.type, input.properties)) {
                throw IllegalArgumentException("Invalid configuration for type: ${input.type}")
            }

            val config = configService.createConfig(input)
            
            // Notify subscribers
            configSubscribers[config.id]?.next(config)
            
            config
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to create component config",
                mapOf(
                    "error" to e.message,
                    "type" to input.type
                )
            )
            throw e
        }
    }

    fun updateComponentConfig(id: String, input: ComponentConfigInput): ComponentConfig {
        return try {
            if (!configService.validateConfig(input.type, input.properties)) {
                throw IllegalArgumentException("Invalid configuration for type: ${input.type}")
            }

            val config = configService.updateConfig(id, input)
            
            // Notify subscribers
            configSubscribers[config.id]?.next(config)
            
            config
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to update component config",
                mapOf(
                    "error" to e.message,
                    "id" to id,
                    "type" to input.type
                )
            )
            throw e
        }
    }

    fun deleteComponentConfig(id: String): Boolean {
        return try {
            val success = configService.deleteConfig(id)
            if (success) {
                // Notify subscribers
                configSubscribers.remove(id)
            }
            success
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to delete component config",
                mapOf(
                    "error" to e.message,
                    "id" to id
                )
            )
            throw e
        }
    }

    fun componentConfigUpdated(id: String): Publisher<ComponentConfig> {
        return Flux.create { sink ->
            configSubscribers[id] = sink
            sink.onDispose {
                configSubscribers.remove(id)
            }
        }
    }
} 