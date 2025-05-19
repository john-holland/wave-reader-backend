package main.kotlin.graphql

import graphql.kickstart.tools.GraphQLQueryResolver
import graphql.kickstart.tools.GraphQLMutationResolver
import graphql.kickstart.tools.GraphQLSubscriptionResolver
import main.kotlin.state.StateMachineRegistry
import main.kotlin.state.StateMachineError
import main.kotlin.state.StateMachineException
import main.kotlin.logging.DataDogLogger
import main.kotlin.features.FeatureFlagService
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import java.util.concurrent.ConcurrentHashMap

class StateMachineResolver(
    private val registry: StateMachineRegistry,
    private val featureFlagService: FeatureFlagService
) : GraphQLQueryResolver, GraphQLMutationResolver, GraphQLSubscriptionResolver {
    
    private val subscribers = ConcurrentHashMap<String, MutableList<Flux<Any>>>()
    private val errorSubscribers = ConcurrentHashMap<String, MutableList<Flux<StateMachineError>>>()
    private val featureFlagSubscribers = ConcurrentHashMap<String, MutableList<Flux<Map<String, Any>>>>()

    fun stateMachine(id: String) = registry.get(id)?.toGraphQL()

    fun stateMachines() = registry.getAll().map { it.toGraphQL() }

    fun teleportTemplate(templateId: String): String? = registry.getTeleportTemplate(templateId)

    fun setTeleportTemplate(templateId: String, template: String): Boolean {
        registry.setTeleportTemplate(templateId, template)
        return true
    }

    fun featureFlags(machineId: String): List<Map<String, Any>> {
        val machine = registry.get(machineId) ?: return emptyList()
        return machine.featureFlags.map { (name, _) ->
            mapOf(
                "name" to name,
                "enabled" to featureFlagService.isEnabled(name, mapOf("machineId" to machineId)),
                "variation" to featureFlagService.getVariation(name, mapOf("machineId" to machineId))
            )
        }
    }

    fun setFeatureFlag(name: String, enabled: Boolean, variation: String?): Boolean {
        if (featureFlagService is FeatureFlagsIOProvider) {
            featureFlagService.setFlag(name, enabled)
            variation?.let { featureFlagService.setVariation(name, it) }
            return true
        }
        return false
    }

    fun transitionStateMachine(id: String, event: String): Map<String, Any> {
        try {
            val machine = registry.get(id) ?: throw IllegalArgumentException("State machine not found: $id")
            val updatedMachine = machine.transition(event, featureFlagService)
            registry.register(updatedMachine)
            
            // Notify subscribers
            subscribers[id]?.forEach { flux ->
                flux.onNext(updatedMachine.toGraphQL())
            }
            
            // Notify feature flag subscribers
            featureFlagSubscribers[id]?.forEach { flux ->
                flux.onNext(updatedMachine.featureFlags)
            }
            
            return updatedMachine.toGraphQL()
        } catch (e: StateMachineException) {
            // Notify error subscribers
            errorSubscribers[id]?.forEach { flux ->
                flux.onNext(e.error)
            }
            
            // Log to DataDog
            DataDogLogger.error(
                "State machine transition failed",
                mapOf(
                    "machineId" to id,
                    "event" to event,
                    "errorCode" to e.error.code,
                    "version" to e.error.version,
                    "featureFlags" to e.error.featureFlags
                )
            )
            
            throw e
        }
    }

    fun stateMachineUpdated(id: String): Publisher<Map<String, Any>> {
        val flux = Flux.create<Map<String, Any>> { sink ->
            subscribers.computeIfAbsent(id) { mutableListOf() }.add(flux)
            sink.onDispose {
                subscribers[id]?.remove(flux)
            }
        }
        return flux
    }

    fun stateMachineError(id: String): Publisher<StateMachineError> {
        val flux = Flux.create<StateMachineError> { sink ->
            errorSubscribers.computeIfAbsent(id) { mutableListOf() }.add(flux)
            sink.onDispose {
                errorSubscribers[id]?.remove(flux)
            }
        }
        return flux
    }

    fun featureFlagUpdated(machineId: String): Publisher<List<Map<String, Any>>> {
        val flux = Flux.create<List<Map<String, Any>>> { sink ->
            featureFlagSubscribers.computeIfAbsent(machineId) { mutableListOf() }.add(flux)
            sink.onDispose {
                featureFlagSubscribers[machineId]?.remove(flux)
            }
        }
        return flux
    }
} 