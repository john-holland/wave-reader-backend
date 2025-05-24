package main.kotlin.template

import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateExceptionHandler
import java.io.StringWriter
import java.nio.file.Paths

class ComponentTemplate {
    private val config = Configuration(Configuration.VERSION_2_3_32).apply {
        setDirectoryForTemplateLoading(Paths.get("src/main/resources/templates").toFile())
        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
    }

    fun render(templateName: String, data: Map<String, Any>): String {
        val template: Template = config.getTemplate("$templateName.ftl")
        val writer = StringWriter()
        template.process(data, writer)
        return writer.toString()
    }

    fun renderStateMachineComponent(machine: Map<String, Any>): String {
        return render("stateMachine", mapOf(
            "machine" to machine,
            "version" to machine["version"],
            "currentState" to machine["currentState"],
            "availableTransitions" to machine["availableTransitions"]
        ))
    }
} 