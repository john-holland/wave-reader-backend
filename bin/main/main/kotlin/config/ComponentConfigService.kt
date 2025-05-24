package main.kotlin.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import main.kotlin.logging.DataDogLogger
import java.util.concurrent.ConcurrentHashMap

data class ComponentConfig(
    val id: String,
    val type: String,
    val properties: Map<String, Any>,
    val version: String,
    val metadata: Map<String, Any>? = null
)

data class ComponentConfigInput(
    val type: String,
    val properties: Map<String, Any>,
    val metadata: Map<String, Any>? = null
)

class ComponentConfigService {
    private val configs = ConcurrentHashMap<String, ComponentConfig>()
    private val mapper = ObjectMapper()

    fun getConfig(id: String): ComponentConfig? {
        return configs[id]
    }

    fun getConfigsByType(type: String): List<ComponentConfig> {
        return configs.values.filter { it.type == type }
    }

    fun createConfig(input: ComponentConfigInput): ComponentConfig {
        val id = java.util.UUID.randomUUID().toString()
        val config = ComponentConfig(
            id = id,
            type = input.type,
            properties = input.properties,
            version = "1.0.0",
            metadata = input.metadata
        )
        
        configs[id] = config
        DataDogLogger.info(
            "Created component config",
            mapOf(
                "id" to id,
                "type" to input.type
            )
        )
        
        return config
    }

    fun updateConfig(id: String, input: ComponentConfigInput): ComponentConfig {
        val existingConfig = configs[id] ?: throw IllegalArgumentException("Config not found: $id")
        
        val newVersion = incrementVersion(existingConfig.version)
        val config = ComponentConfig(
            id = id,
            type = input.type,
            properties = input.properties,
            version = newVersion,
            metadata = input.metadata
        )
        
        configs[id] = config
        DataDogLogger.info(
            "Updated component config",
            mapOf(
                "id" to id,
                "type" to input.type,
                "version" to newVersion
            )
        )
        
        return config
    }

    fun deleteConfig(id: String): Boolean {
        return if (configs.remove(id) != null) {
            DataDogLogger.info(
                "Deleted component config",
                mapOf("id" to id)
            )
            true
        } else {
            false
        }
    }

    fun validateConfig(type: String, properties: Map<String, Any>): Boolean {
        return try {
            when (type) {
                "ecommerce" -> validateEcommerceConfig(properties)
                "donation" -> validateDonationConfig(properties)
                else -> true // Allow unknown types
            }
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to validate config",
                mapOf(
                    "type" to type,
                    "error" to e.message
                )
            )
            false
        }
    }

    private fun validateEcommerceConfig(properties: Map<String, Any>): Boolean {
        val requiredFields = listOf("currency", "taxRate", "shippingOptions")
        return requiredFields.all { properties.containsKey(it) }
    }

    private fun validateDonationConfig(properties: Map<String, Any>): Boolean {
        val requiredFields = listOf("defaultAmount", "currency", "recurringOptions")
        return requiredFields.all { properties.containsKey(it) }
    }

    private fun incrementVersion(version: String): String {
        val parts = version.split(".")
        return if (parts.size == 3) {
            val patch = parts[2].toInt() + 1
            "${parts[0]}.${parts[1]}.$patch"
        } else {
            "1.0.0"
        }
    }

    // Example configurations
    fun getDefaultEcommerceConfig(): ComponentConfig {
        return ComponentConfig(
            id = "default-ecommerce",
            type = "ecommerce",
            properties = mapOf(
                "currency" to "USD",
                "taxRate" to 0.08,
                "shippingOptions" to listOf(
                    mapOf(
                        "name" to "Standard",
                        "price" to 5.99,
                        "deliveryDays" to 3
                    ),
                    mapOf(
                        "name" to "Express",
                        "price" to 12.99,
                        "deliveryDays" to 1
                    )
                ),
                "paymentMethods" to listOf("credit_card", "paypal"),
                "inventoryManagement" to true
            ),
            version = "1.0.0"
        )
    }

    fun getDefaultDonationConfig(): ComponentConfig {
        return ComponentConfig(
            id = "default-donation",
            type = "donation",
            properties = mapOf(
                "defaultAmount" to 25.00,
                "currency" to "USD",
                "recurringOptions" to listOf(
                    mapOf(
                        "interval" to "monthly",
                        "amount" to 25.00
                    ),
                    mapOf(
                        "interval" to "yearly",
                        "amount" to 300.00
                    )
                ),
                "suggestedAmounts" to listOf(10.00, 25.00, 50.00, 100.00),
                "allowCustomAmount" to true
            ),
            version = "1.0.0"
        )
    }
} 