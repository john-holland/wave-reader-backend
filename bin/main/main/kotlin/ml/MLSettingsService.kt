package main.kotlin.ml

import main.kotlin.config.ComponentConfig
import main.kotlin.logging.DataDogLogger
import java.util.concurrent.ConcurrentHashMap

data class MLPrediction(
    val confidence: Double,
    val suggestedSettings: Map<String, Any>,
    val factors: List<String>
)

class MLSettingsService {
    private val predictionCache = ConcurrentHashMap<String, MLPrediction>()
    private val historicalData = ConcurrentHashMap<String, MutableList<Map<String, Any>>>()

    fun predictDefaultSettings(
        type: String,
        context: Map<String, Any>,
        historicalData: List<Map<String, Any>>
    ): MLPrediction {
        return try {
            // Check cache first
            val cacheKey = "${type}_${context.hashCode()}"
            predictionCache[cacheKey]?.let { return it }

            // Generate prediction based on type
            val prediction = when (type) {
                "donation" -> predictDonationSettings(context, historicalData)
                "ecommerce" -> predictEcommerceSettings(context, historicalData)
                else -> throw IllegalArgumentException("Unsupported type for ML prediction: $type")
            }

            // Cache the prediction
            predictionCache[cacheKey] = prediction
            prediction
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to predict default settings",
                mapOf(
                    "type" to type,
                    "error" to e.message
                )
            )
            throw e
        }
    }

    private fun predictDonationSettings(
        context: Map<String, Any>,
        historicalData: List<Map<String, Any>>
    ): MLPrediction {
        // Analyze historical donation patterns
        val avgAmount = historicalData.mapNotNull { it["amount"] as? Double }.average()
        val commonIntervals = historicalData
            .mapNotNull { it["interval"] as? String }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: "monthly"

        // Consider user context (location, previous donations, etc.)
        val suggestedAmount = when {
            context["previousDonations"] != null -> avgAmount * 1.1 // 10% increase
            context["location"] == "US" -> 25.0
            context["location"] == "EU" -> 20.0
            else -> 15.0
        }

        return MLPrediction(
            confidence = 0.85,
            suggestedSettings = mapOf(
                "defaultAmount" to suggestedAmount,
                "currency" to (context["currency"] ?: "USD"),
                "recurringOptions" to listOf(
                    mapOf(
                        "interval" to commonIntervals,
                        "amount" to suggestedAmount
                    ),
                    mapOf(
                        "interval" to "yearly",
                        "amount" to suggestedAmount * 12
                    )
                ),
                "suggestedAmounts" to listOf(
                    suggestedAmount * 0.5,
                    suggestedAmount,
                    suggestedAmount * 2,
                    suggestedAmount * 4
                ),
                "allowCustomAmount" to true
            ),
            factors = listOf(
                "Historical average: $avgAmount",
                "Common interval: $commonIntervals",
                "Location: ${context["location"]}"
            )
        )
    }

    private fun predictEcommerceSettings(
        context: Map<String, Any>,
        historicalData: List<Map<String, Any>>
    ): MLPrediction {
        // Analyze historical purchase patterns
        val avgOrderValue = historicalData.mapNotNull { it["orderValue"] as? Double }.average()
        val commonPaymentMethods = historicalData
            .mapNotNull { it["paymentMethod"] as? String }
            .distinct()

        return MLPrediction(
            confidence = 0.82,
            suggestedSettings = mapOf(
                "currency" to (context["currency"] ?: "USD"),
                "taxRate" to when (context["location"]) {
                    "US" -> 0.08
                    "EU" -> 0.20
                    else -> 0.10
                },
                "shippingOptions" to listOf(
                    mapOf(
                        "name" to "Standard",
                        "price" to (avgOrderValue * 0.1).coerceIn(5.0, 15.0),
                        "deliveryDays" to 3
                    ),
                    mapOf(
                        "name" to "Express",
                        "price" to (avgOrderValue * 0.2).coerceIn(10.0, 30.0),
                        "deliveryDays" to 1
                    )
                ),
                "paymentMethods" to commonPaymentMethods.ifEmpty { listOf("credit_card", "paypal") },
                "inventoryManagement" to true
            ),
            factors = listOf(
                "Average order value: $avgOrderValue",
                "Common payment methods: ${commonPaymentMethods.joinToString()}",
                "Location: ${context["location"]}"
            )
        )
    }

    fun recordOutcome(
        type: String,
        settings: Map<String, Any>,
        outcome: Map<String, Any>
    ) {
        try {
            val data = historicalData.getOrPut(type) { mutableListOf() }
            data.add(settings + outcome)
            
            // Clear prediction cache for this type
            predictionCache.keys.filter { it.startsWith("${type}_") }
                .forEach { predictionCache.remove(it) }

            DataDogLogger.info(
                "Recorded ML outcome",
                mapOf(
                    "type" to type,
                    "outcome" to outcome
                )
            )
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to record ML outcome",
                mapOf(
                    "type" to type,
                    "error" to e.message
                )
            )
        }
    }
} 