package main.kotlin.template

import com.fasterxml.jackson.databind.ObjectMapper
import main.kotlin.logging.DataDogLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap

interface TemplateProvider {
    fun getTemplate(templateId: String, context: Map<String, Any>): String
    fun renderTemplate(template: String, context: Map<String, Any>): String
}

class TeleportHQProvider : TemplateProvider {
    private val templates = ConcurrentHashMap<String, String>()
    private val mapper = ObjectMapper()

    override fun getTemplate(templateId: String, context: Map<String, Any>): String {
        return templates[templateId] ?: throw IllegalArgumentException("Template not found: $templateId")
    }

    override fun renderTemplate(template: String, context: Map<String, Any>): String {
        return template.replace(Regex("\\{\\{([^}]+)\\}\\}")) { matchResult ->
            val key = matchResult.groupValues[1].trim()
            context[key]?.toString() ?: ""
        }
    }

    fun setTemplate(templateId: String, template: String) {
        templates[templateId] = template
    }
}

class DotCMSProvider(
    private val baseUrl: String,
    private val apiKey: String
) : TemplateProvider {
    private val client = HttpClient.newBuilder().build()
    private val mapper = ObjectMapper()

    override fun getTemplate(templateId: String, context: Map<String, Any>): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/templates/$templateId"))
            .header("Authorization", "Bearer $apiKey")
            .GET()
            .build()

        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val templateData = mapper.readTree(response.body())
                return templateData["template"].asText()
            } else {
                throw RuntimeException("Failed to fetch template: ${response.statusCode()}")
            }
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to fetch DotCMS template",
                mapOf(
                    "templateId" to templateId,
                    "error" to e.message
                )
            )
            throw e
        }
    }

    override fun renderTemplate(template: String, context: Map<String, Any>): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/v1/templates/render"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(mapOf(
                "template" to template,
                "context" to context
            ))))
            .build()

        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val result = mapper.readTree(response.body())
                return result["rendered"].asText()
            } else {
                throw RuntimeException("Failed to render template: ${response.statusCode()}")
            }
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to render DotCMS template",
                mapOf(
                    "template" to template,
                    "error" to e.message
                )
            )
            throw e
        }
    }
}

class TemplateService(
    private val teleportProvider: TeleportHQProvider,
    private val dotCMSProvider: DotCMSProvider
) {
    fun getTemplate(provider: String, templateId: String, context: Map<String, Any>): String {
        return when (provider.lowercase()) {
            "teleport" -> teleportProvider.getTemplate(templateId, context)
            "dotcms" -> dotCMSProvider.getTemplate(templateId, context)
            else -> throw IllegalArgumentException("Unknown template provider: $provider")
        }
    }

    fun renderTemplate(provider: String, template: String, context: Map<String, Any>): String {
        return when (provider.lowercase()) {
            "teleport" -> teleportProvider.renderTemplate(template, context)
            "dotcms" -> dotCMSProvider.renderTemplate(template, context)
            else -> throw IllegalArgumentException("Unknown template provider: $provider")
        }
    }

    fun setTeleportTemplate(templateId: String, template: String) {
        teleportProvider.setTemplate(templateId, template)
    }
} 