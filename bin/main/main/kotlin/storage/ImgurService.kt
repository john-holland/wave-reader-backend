package main.kotlin.storage

import com.fasterxml.jackson.databind.ObjectMapper
import main.kotlin.logging.DataDogLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

data class ImgurResponse(
    val data: ImgurData,
    val success: Boolean,
    val status: Int
)

data class ImgurData(
    val id: String,
    val title: String?,
    val description: String?,
    val datetime: Long,
    val type: String,
    val animated: Boolean,
    val width: Int,
    val height: Int,
    val size: Int,
    val views: Int,
    val bandwidth: Long,
    val vote: String?,
    val favorite: Boolean,
    val nsfw: Boolean?,
    val section: String?,
    val account_url: String?,
    val account_id: Int?,
    val is_ad: Boolean,
    val in_most_viral: Boolean,
    val has_sound: Boolean,
    val tags: List<String>,
    val ad_type: Int,
    val ad_url: String,
    val edited: String,
    val in_gallery: Boolean,
    val deletehash: String?,
    val name: String?,
    val link: String
)

class ImgurService(
    private val clientId: String,
    private val clientSecret: String
) {
    private val client = HttpClient.newBuilder().build()
    private val mapper = ObjectMapper()

    fun uploadImage(
        imageData: ByteArray,
        title: String? = null,
        description: String? = null
    ): ImgurData {
        val base64Image = Base64.getEncoder().encodeToString(imageData)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.imgur.com/3/image"))
            .header("Authorization", "Client-ID $clientId")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(
                buildString {
                    append("image=$base64Image")
                    title?.let { append("&title=${it.encodeUrl()}") }
                    description?.let { append("&description=${it.encodeUrl()}") }
                }
            ))
            .build()

        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val imgurResponse = mapper.readValue(response.body(), ImgurResponse::class.java)
                if (imgurResponse.success) {
                    return imgurResponse.data
                } else {
                    throw RuntimeException("Imgur API error: ${imgurResponse.status}")
                }
            } else {
                throw RuntimeException("Failed to upload image: ${response.statusCode()}")
            }
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to upload image to Imgur",
                mapOf(
                    "error" to e.message,
                    "title" to title
                )
            )
            throw e
        }
    }

    fun deleteImage(deleteHash: String): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.imgur.com/3/image/$deleteHash"))
            .header("Authorization", "Client-ID $clientId")
            .DELETE()
            .build()

        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val result = mapper.readTree(response.body())
                return result["success"].asBoolean()
            } else {
                throw RuntimeException("Failed to delete image: ${response.statusCode()}")
            }
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to delete image from Imgur",
                mapOf(
                    "error" to e.message,
                    "deleteHash" to deleteHash
                )
            )
            throw e
        }
    }

    private fun String.encodeUrl(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
} 