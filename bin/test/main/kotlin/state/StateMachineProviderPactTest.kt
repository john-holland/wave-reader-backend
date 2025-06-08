package main.kotlin.state

import au.com.dius.pact.provider.junit5.PactVerificationContext
import au.com.dius.pact.provider.junitsupport.Provider
import au.com.dius.pact.provider.junitsupport.State
import au.com.dius.pact.provider.junitsupport.loader.PactBroker
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import main.kotlin.features.FeatureFlagService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Provider("log-view-service")
@PactBroker
class StateMachineProviderPactTest {

    @MockBean
    private lateinit var featureFlagService: FeatureFlagService

    @TestTemplate
    @ExtendWith(PactVerificationSpringProvider::class)
    fun pactVerificationTestTemplate(context: PactVerificationContext) {
        context.verifyInteraction()
    }

    @State("state machine is in base state")
    fun toBaseState() {
        // Setup initial state
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
            ),
            featureFlags = mapOf(
                "enableEditing" to "true"
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

        // Verify initial state
        assertNotNull(stateMachine)
        assertEquals("base", stateMachine.currentState)
        assertEquals("base", (stateMachine.context as LogViewContext).viewState)
    }

    @State("state machine transitions to viewing state")
    fun toViewingState() {
        // Setup state machine with viewing state
        val stateMachine = StateMachine(
            id = "log-view-machine",
            currentState = "viewing",
            context = LogViewContext(viewState = "viewing"),
            version = "1.0",
            transitions = mapOf(
                "viewing" to listOf("base", "editing")
            ),
            actions = mapOf(
                "base" to { context: LogViewContext ->
                    context.copy(viewState = "base")
                },
                "editing" to { context: LogViewContext ->
                    context.copy(viewState = "editing")
                }
            ),
            featureFlags = mapOf(
                "enableEditing" to "true"
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

        // Verify viewing state
        assertNotNull(stateMachine)
        assertEquals("viewing", stateMachine.currentState)
        assertEquals("viewing", (stateMachine.context as LogViewContext).viewState)
    }
} 