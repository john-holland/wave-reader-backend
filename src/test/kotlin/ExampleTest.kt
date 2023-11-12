package main.kotlin

// credit https://github.com/christierney/kotlin-jetty-jersey-example/blob/master/src/test/kotlin/example/MyResourceSpec.kt
import org.glassfish.jersey.message.internal.StringBuilderUtils

import java.util.*
import kotlin.test.*

data class MyResource(
        val id: Int,
        val msg: String,
        val date: Date,
        val getIt: () -> MyResource?,
        val reverse: () -> String)

class MyResourceTests {
    var resource: MyResource? = null

    @Before
    fun setup() {
        resource = MyResource(
            id = 1,
            msg = "Got it!",
            date = Calendar.getInstance().time,
            getIt = { return@MyResource resource },
            // why the type mismatch and missing string, maybe wrong version of kotlin?
            reverse = { StringBuilder(resource?.msg).reverse().toString() }
        )
    }

    @Test
    fun `should return a MyData with an id, message, and date` {
        val data = resource?.getIt?.invoke()
        assertEquals(1, data?.id)
        assertEquals("Got it!", data?.msg)
        assertTrue( data?.date is Date )
    }

    @Test
    fun `should return a MyData with msg reversed and id and date the same`() {
        val data = resource?.getIt?.invoke()
        assertEquals(1, data?.id)
        assertEquals("Got it!", data?.msg)
        assertTrue( data?.date is Date )
    }

    @Test
    fun `MyResourceTestsMyDataWithMsgReversedAndIdAndDataTheSame`() {
        val data = resource?.reverse?.invoke()
        assertEquals("!ti toG", data)
    }
}