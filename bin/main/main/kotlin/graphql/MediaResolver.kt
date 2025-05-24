package main.kotlin.graphql

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.schema.DataFetchingEnvironment
import main.kotlin.logging.DataDogLogger
import main.kotlin.storage.ImgurService
import main.kotlin.template.TemplateService
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class MediaResolver(
    private val imgurService: ImgurService,
    private val templateService: TemplateService
) {
    private val templateSubscribers = ConcurrentHashMap<String, FluxSink<Template>>()
    private val imageSubscribers = ConcurrentHashMap<String, FluxSink<Image>>()
    private val mapper = ObjectMapper()

    fun uploadImage(env: DataFetchingEnvironment): Image {
        val file = env.getArgument<Any>("file")
        val input = env.getArgument<Map<String, Any>>("input")
        
        try {
            val imageData = when (file) {
                is ByteArray -> file
                is String -> file.toByteArray()
                else -> throw IllegalArgumentException("Unsupported file type")
            }

            val title = input["title"] as? String
            val description = input["description"] as? String

            val imgurData = imgurService.uploadImage(imageData, title, description)
            
            val image = Image(
                id = imgurData.id,
                title = imgurData.title,
                description = imgurData.description,
                link = imgurData.link,
                deleteHash = imgurData.deletehash,
                width = imgurData.width,
                height = imgurData.height,
                size = imgurData.size,
                type = imgurData.type,
                animated = imgurData.animated
            )

            // Notify subscribers
            imageSubscribers.values.forEach { sink ->
                sink.next(image)
            }

            return image
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to upload image",
                mapOf(
                    "error" to e.message,
                    "input" to input
                )
            )
            throw e
        }
    }

    fun deleteImage(deleteHash: String): Boolean {
        return try {
            val success = imgurService.deleteImage(deleteHash)
            if (success) {
                DataDogLogger.info(
                    "Image deleted successfully",
                    mapOf("deleteHash" to deleteHash)
                )
            }
            success
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to delete image",
                mapOf(
                    "error" to e.message,
                    "deleteHash" to deleteHash
                )
            )
            throw e
        }
    }

    fun getTemplate(id: String): Template? {
        return try {
            val template = templateService.getTemplate(id)
            Template(
                id = id,
                name = template.name,
                content = template.content,
                provider = template.provider,
                metadata = template.metadata
            )
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to get template",
                mapOf(
                    "error" to e.message,
                    "id" to id
                )
            )
            null
        }
    }

    fun getTemplates(provider: String?): List<Template> {
        return try {
            templateService.getTemplates(provider).map { template ->
                Template(
                    id = template.id,
                    name = template.name,
                    content = template.content,
                    provider = template.provider,
                    metadata = template.metadata
                )
            }
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to get templates",
                mapOf(
                    "error" to e.message,
                    "provider" to provider
                )
            )
            emptyList()
        }
    }

    fun createTemplate(input: TemplateInput): Template {
        return try {
            val template = templateService.createTemplate(
                name = input.name,
                content = input.content,
                provider = input.provider,
                metadata = input.metadata
            )

            val result = Template(
                id = template.id,
                name = template.name,
                content = template.content,
                provider = template.provider,
                metadata = template.metadata
            )

            // Notify subscribers
            templateSubscribers[template.id]?.next(result)

            result
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to create template",
                mapOf(
                    "error" to e.message,
                    "input" to input
                )
            )
            throw e
        }
    }

    fun updateTemplate(id: String, input: TemplateInput): Template {
        return try {
            val template = templateService.updateTemplate(
                id = id,
                name = input.name,
                content = input.content,
                provider = input.provider,
                metadata = input.metadata
            )

            val result = Template(
                id = template.id,
                name = template.name,
                content = template.content,
                provider = template.provider,
                metadata = template.metadata
            )

            // Notify subscribers
            templateSubscribers[template.id]?.next(result)

            result
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to update template",
                mapOf(
                    "error" to e.message,
                    "id" to id,
                    "input" to input
                )
            )
            throw e
        }
    }

    fun deleteTemplate(id: String): Boolean {
        return try {
            val success = templateService.deleteTemplate(id)
            if (success) {
                // Notify subscribers
                templateSubscribers.remove(id)
                DataDogLogger.info(
                    "Template deleted successfully",
                    mapOf("id" to id)
                )
            }
            success
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to delete template",
                mapOf(
                    "error" to e.message,
                    "id" to id
                )
            )
            throw e
        }
    }

    fun templateUpdated(id: String): Publisher<Template> {
        return Flux.create { sink ->
            templateSubscribers[id] = sink
            sink.onDispose {
                templateSubscribers.remove(id)
            }
        }
    }

    fun imageUploaded(): Publisher<Image> {
        return Flux.create { sink ->
            val id = java.util.UUID.randomUUID().toString()
            imageSubscribers[id] = sink
            sink.onDispose {
                imageSubscribers.remove(id)
            }
        }
    }
}

data class Template(
    val id: String,
    val name: String,
    val content: String,
    val provider: String,
    val metadata: Map<String, Any>?
)

data class TemplateInput(
    val name: String,
    val content: String,
    val provider: String,
    val metadata: Map<String, Any>?
)

data class Image(
    val id: String,
    val title: String?,
    val description: String?,
    val link: String,
    val deleteHash: String?,
    val width: Int,
    val height: Int,
    val size: Int,
    val type: String,
    val animated: Boolean
) 