package main.kotlin.logging

import datadog.trace.api.Trace
import datadog.trace.api.TraceScope
import io.opentracing.util.GlobalTracer
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object DataDogLogger {
    @Trace
    fun error(message: String, metadata: Map<String, Any>) {
        val tracer = GlobalTracer.get()
        val scope = tracer.buildSpan("state_machine_error")
            .withTag("error", true)
            .startActive(true)

        try {
            scope.span().apply {
                setTag("error.type", "StateMachineError")
                metadata.forEach { (key, value) -> setTag(key, value.toString()) }
            }
            
            logger.error { "$message - Metadata: $metadata" }
        } finally {
            scope.close()
        }
    }

    @Trace
    fun info(message: String, metadata: Map<String, Any>) {
        val tracer = GlobalTracer.get()
        val scope = tracer.buildSpan("state_machine_info")
            .startActive(true)

        try {
            scope.span().apply {
                metadata.forEach { (key, value) -> setTag(key, value.toString()) }
            }
            
            logger.info { "$message - Metadata: $metadata" }
        } finally {
            scope.close()
        }
    }
} 