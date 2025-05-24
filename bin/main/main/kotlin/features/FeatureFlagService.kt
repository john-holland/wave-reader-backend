package main.kotlin.features

import com.launchdarkly.sdk.LDUser
import com.launchdarkly.sdk.server.LDClient
import main.kotlin.logging.DataDogLogger
import java.util.concurrent.ConcurrentHashMap

interface FeatureFlagProvider {
    fun isEnabled(flag: String, context: Map<String, Any>): Boolean
    fun getVariation(flag: String, context: Map<String, Any>): String?
}

class FeatureFlagsIOProvider : FeatureFlagProvider {
    private val flags = ConcurrentHashMap<String, Boolean>()
    private val variations = ConcurrentHashMap<String, String>()

    override fun isEnabled(flag: String, context: Map<String, Any>): Boolean {
        return flags[flag] ?: false
    }

    override fun getVariation(flag: String, context: Map<String, Any>): String? {
        return variations[flag]
    }

    fun setFlag(flag: String, enabled: Boolean) {
        flags[flag] = enabled
    }

    fun setVariation(flag: String, variation: String) {
        variations[flag] = variation
    }
}

class LaunchDarklyProvider(private val client: LDClient) : FeatureFlagProvider {
    override fun isEnabled(flag: String, context: Map<String, Any>): Boolean {
        val user = LDUser.Builder(context["key"] as? String ?: "anonymous")
            .apply {
                context.forEach { (key, value) ->
                    when (value) {
                        is String -> custom(key, value)
                        is Number -> custom(key, value.toDouble())
                        is Boolean -> custom(key, value)
                    }
                }
            }
            .build()
        
        return client.boolVariation(flag, user, false)
    }

    override fun getVariation(flag: String, context: Map<String, Any>): String? {
        val user = LDUser.Builder(context["key"] as? String ?: "anonymous")
            .apply {
                context.forEach { (key, value) ->
                    when (value) {
                        is String -> custom(key, value)
                        is Number -> custom(key, value.toDouble())
                        is Boolean -> custom(key, value)
                    }
                }
            }
            .build()
        
        return client.stringVariation(flag, user, null)
    }
}

class FeatureFlagService(
    private val primaryProvider: FeatureFlagProvider,
    private val fallbackProvider: FeatureFlagProvider? = null
) {
    fun isEnabled(flag: String, context: Map<String, Any>): Boolean {
        return try {
            primaryProvider.isEnabled(flag, context)
        } catch (e: Exception) {
            DataDogLogger.error(
                "Feature flag check failed with primary provider",
                mapOf(
                    "flag" to flag,
                    "error" to e.message,
                    "fallback" to (fallbackProvider != null)
                )
            )
            
            fallbackProvider?.isEnabled(flag, context) ?: false
        }
    }

    fun getVariation(flag: String, context: Map<String, Any>): String? {
        return try {
            primaryProvider.getVariation(flag, context)
        } catch (e: Exception) {
            DataDogLogger.error(
                "Feature flag variation check failed with primary provider",
                mapOf(
                    "flag" to flag,
                    "error" to e.message,
                    "fallback" to (fallbackProvider != null)
                )
            )
            
            fallbackProvider?.getVariation(flag, context)
        }
    }
} 