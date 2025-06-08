package main.kotlin.state

import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.RequestResponsePact
import au.com.dius.pact.core.model.annotations.Pact
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(PactConsumerTestExt::class)
class StateMachinePactTest {

    @Pact(consumer = "wave-reader-state-machine", provider = "log-view-service")
    fun createPact(builder: PactDslWithProvider): RequestResponsePact {
        return builder
            .given("state machine is in base state")
            .uponReceiving("a request to transition to viewing state")
            .path("/api/state-machine/transition")
            .method(HttpMethod.POST.name)
            .body("""
                {
                    "machineId": "log-view-machine",
                    "event": "viewing",
                    "clientId": "test-client"
                }
            """.trimIndent())
            .willRespondWith()
            .status(HttpStatus.OK.value())
            .body("""
                {
                    "id": "log-view-machine",
                    "currentState": "viewing",
                    "context": {
                        "viewState": "viewing",
                        "timestamp": 1234567890
                    },
                    "version": "1.0",
                    "availableTransitions": ["base", "editing"],
                    "featureFlags": {
                        "enableEditing": true
                    }
                }
            """.trimIndent())
            .toPact()
    }

    @Test
    @PactTestFor(pactMethod = "createPact")
    fun testStateTransition(mockServer: MockServer) {
        // Create test state machine
        val stateMachine = StateMachine(
            id = "log-view-machine",
            currentState = "base",
            context = LogViewContext(),
            version = "1.0",
            transitions = mapOf(
                "base" to listOf("viewing", "editing")
            ),
            actions = mapOf(
                "viewing" to { context: LogViewContext ->
                    context.copy(viewState = "viewing")
                }
            )
        )

        // Register test client
        val clientConfig = ClientConfig(
            id = "test-client",
            type = ClientType.API,
            location = ClientLocation.SERVER,
            capabilities = setOf(ClientCapability.STATE_TRANSITION)
        )
        stateMachine.registerClient(clientConfig)

        // Perform transition
        val result = stateMachine.transition("viewing")

        // Verify results
        assertNotNull(result)
        assertEquals("viewing", result.currentState)
        assertEquals("viewing", (result.context as LogViewContext).viewState)
        assertEquals(listOf("base", "editing"), result.transitions["viewing"])
    }
}

// Test context class
data class LogViewContext(
    val viewState: String = "base",
    val timestamp: Long = System.currentTimeMillis()
) 