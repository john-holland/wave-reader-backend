package main.kotlin.state

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import datadog.trace.api.Trace
import datadog.trace.api.TraceScope
import main.kotlin.features.FeatureFlagService
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class StateMachineError(
    val code: String,
    val message: String,
    val version: String,
    val cause: String? = null,
    val subMachineId: String? = null,
    val featureFlags: Map<String, Boolean> = emptyMap()
)

@Serializable
data class StateMachine<T>(
    val id: String,
    val currentState: String,
    val context: T,
    val version: String,
    val transitions: Map<String, List<String>>,
    val actions: Map<String, (T) -> T>,
    val subMachines: Map<String, StateMachine<*>> = emptyMap(),
    val errorHandlers: Map<String, (StateMachineError) -> StateMachine<T>> = emptyMap(),
    val fallbackHandler: ((StateMachineError) -> StateMachine<T>)? = null,
    val featureFlags: Map<String, String> = emptyMap()
) {
    private val teleportTemplates = ConcurrentHashMap<String, String>()

    fun transition(event: String, featureFlagService: FeatureFlagService? = null): StateMachine<T> {
        return try {
            // Check feature flags before transition
            val activeFeatureFlags = featureFlagService?.let { service ->
                featureFlags.mapValues { (flag, _) ->
                    service.isEnabled(flag, mapOf(
                        "machineId" to id,
                        "currentState" to currentState,
                        "event" to event,
                        "version" to version
                    ))
                }
            } ?: emptyMap()

            val validTransitions = transitions[currentState] ?: emptyList()
            if (event !in validTransitions) {
                throw StateMachineException(
                    StateMachineError(
                        code = "INVALID_TRANSITION",
                        message = "Invalid transition from $currentState to $event",
                        version = version,
                        featureFlags = activeFeatureFlags
                    )
                )
            }
            
            val action = actions[event] ?: { it }
            val newContext = action(context)
            
            // Handle sub-machines with feature flags
            val updatedSubMachines = subMachines.mapValues { (_, subMachine) ->
                try {
                    subMachine.transition(event, featureFlagService)
                } catch (e: StateMachineException) {
                    handleSubMachineError(e.error)
                    subMachine
                }
            }
            
            copy(
                currentState = event,
                context = newContext,
                subMachines = updatedSubMachines,
                featureFlags = activeFeatureFlags
            )
        } catch (e: Exception) {
            when (e) {
                is StateMachineException -> handleError(e.error)
                else -> handleError(
                    StateMachineError(
                        code = "UNEXPECTED_ERROR",
                        message = e.message ?: "An unexpected error occurred",
                        version = version,
                        cause = e.toString(),
                        featureFlags = featureFlags.mapValues { false }
                    )
                )
            }
        }
    }

    @Trace
    private fun handleError(error: StateMachineError): StateMachine<T> {
        val handler = errorHandlers[error.code] ?: fallbackHandler
        return if (handler != null) {
            try {
                handler(error)
            } catch (e: Exception) {
                DataDogLogger.error(
                    "State machine error handling failed",
                    mapOf(
                        "machineId" to id,
                        "errorCode" to error.code,
                        "version" to version,
                        "cause" to e.toString(),
                        "featureFlags" to error.featureFlags
                    )
                )
                throw StateMachineException(error)
            }
        } else {
            throw StateMachineException(error)
        }
    }

    private fun handleSubMachineError(error: StateMachineError): StateMachine<T> {
        return handleError(error.copy(subMachineId = error.subMachineId))
    }

    fun toGraphQL(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "currentState" to currentState,
            "context" to Json.encodeToString(Json.serializersModule.serializer(), context),
            "version" to version,
            "availableTransitions" to (transitions[currentState] ?: emptyList()),
            "subMachines" to subMachines.mapValues { it.value.toGraphQL() },
            "featureFlags" to featureFlags
        )
    }

    fun setTeleportTemplate(templateId: String, template: String) {
        teleportTemplates[templateId] = template
    }

    fun getTeleportTemplate(templateId: String): String? = teleportTemplates[templateId]
}

class StateMachineException(val error: StateMachineError) : Exception(error.message)

class StateMachineRegistry {
    private val machines = mutableMapOf<String, StateMachine<*>>()
    private val teleportTemplates = ConcurrentHashMap<String, String>()

    fun <T> register(machine: StateMachine<T>) {
        machines[machine.id] = machine
    }

    fun get(id: String): StateMachine<*>? = machines[id]

    fun getAll(): List<StateMachine<*>> = machines.values.toList()

    fun setTeleportTemplate(templateId: String, template: String) {
        teleportTemplates[templateId] = template
    }

    fun getTeleportTemplate(templateId: String): String? = teleportTemplates[templateId]
} 