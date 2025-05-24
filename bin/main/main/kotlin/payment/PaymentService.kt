package main.kotlin.payment

import com.fasterxml.jackson.databind.ObjectMapper
import com.squareup.square.SquareClient
import com.squareup.square.models.*
import main.kotlin.logging.DataDogLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap

data class PaymentRequest(
    val amount: Double,
    val currency: String,
    val paymentMethod: String,
    val metadata: Map<String, String>? = null
)

data class PaymentResponse(
    val id: String,
    val status: String,
    val amount: Double,
    val currency: String,
    val paymentMethod: String,
    val metadata: Map<String, String>? = null,
    val error: String? = null
)

data class DonationRecord(
    val id: String,
    val amount: Double,
    val currency: String,
    val paymentMethod: String,
    val status: String,
    val timestamp: Long,
    val metadata: Map<String, String>? = null
)

class PaymentService(
    private val squareClient: SquareClient,
    private val googlePayMerchantId: String,
    private val googlePayApiKey: String
) {
    private val client = HttpClient.newBuilder().build()
    private val mapper = ObjectMapper()
    private val donationRecords = ConcurrentHashMap<String, DonationRecord>()

    fun processPayment(request: PaymentRequest): PaymentResponse {
        return try {
            when (request.paymentMethod.lowercase()) {
                "square" -> processSquarePayment(request)
                "googlepay" -> processGooglePayPayment(request)
                else -> throw IllegalArgumentException("Unsupported payment method: ${request.paymentMethod}")
            }
        } catch (e: Exception) {
            DataDogLogger.error(
                "Payment processing failed",
                mapOf(
                    "error" to e.message,
                    "paymentMethod" to request.paymentMethod,
                    "amount" to request.amount
                )
            )
            PaymentResponse(
                id = java.util.UUID.randomUUID().toString(),
                status = "failed",
                amount = request.amount,
                currency = request.currency,
                paymentMethod = request.paymentMethod,
                metadata = request.metadata,
                error = e.message
            )
        }
    }

    private fun processSquarePayment(request: PaymentRequest): PaymentResponse {
        val amount = (request.amount * 100).toLong() // Convert to cents
        val currency = request.currency.uppercase()

        val paymentRequest = CreatePaymentRequest.Builder(
            sourceId = request.metadata?.get("sourceId") ?: throw IllegalArgumentException("Square sourceId is required"),
            amountMoney = Money.Builder()
                .amount(amount)
                .currency(currency)
                .build()
        )
            .locationId(request.metadata?.get("locationId"))
            .referenceId(request.metadata?.get("referenceId"))
            .note(request.metadata?.get("note"))
            .build()

        val response = squareClient.paymentsApi.createPayment(paymentRequest)
        val payment = response.payment

        return PaymentResponse(
            id = payment.id,
            status = payment.status,
            amount = payment.amountMoney.amount.toDouble() / 100,
            currency = payment.amountMoney.currency,
            paymentMethod = "square",
            metadata = request.metadata
        )
    }

    private fun processGooglePayPayment(request: PaymentRequest): PaymentResponse {
        val requestBody = mapOf(
            "merchantId" to googlePayMerchantId,
            "amount" to request.amount,
            "currency" to request.currency,
            "paymentToken" to request.metadata?.get("paymentToken")
                ?: throw IllegalArgumentException("Google Pay payment token is required")
        )

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://payments.google.com/api/v1/process"))
            .header("Authorization", "Bearer $googlePayApiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
            .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200) {
            val result = mapper.readTree(response.body())
            return PaymentResponse(
                id = result["paymentId"].asText(),
                status = "completed",
                amount = request.amount,
                currency = request.currency,
                paymentMethod = "googlepay",
                metadata = request.metadata
            )
        } else {
            throw RuntimeException("Google Pay API error: ${response.statusCode()}")
        }
    }

    fun recordDonation(payment: PaymentResponse): DonationRecord {
        val record = DonationRecord(
            id = payment.id,
            amount = payment.amount,
            currency = payment.currency,
            paymentMethod = payment.paymentMethod,
            status = payment.status,
            timestamp = System.currentTimeMillis(),
            metadata = payment.metadata
        )
        
        donationRecords[record.id] = record
        
        DataDogLogger.info(
            "Donation recorded",
            mapOf(
                "id" to record.id,
                "amount" to record.amount,
                "currency" to record.currency,
                "paymentMethod" to record.paymentMethod
            )
        )
        
        return record
    }

    fun getDonationHistory(
        startTime: Long? = null,
        endTime: Long? = null,
        paymentMethod: String? = null
    ): List<DonationRecord> {
        return donationRecords.values
            .filter { record ->
                (startTime == null || record.timestamp >= startTime) &&
                (endTime == null || record.timestamp <= endTime) &&
                (paymentMethod == null || record.paymentMethod == paymentMethod)
            }
            .sortedByDescending { it.timestamp }
    }

    fun getDonationStats(): Map<String, Any> {
        val records = donationRecords.values
        val totalAmount = records.sumOf { it.amount }
        val averageAmount = records.map { it.amount }.average()
        val paymentMethodDistribution = records
            .groupBy { it.paymentMethod }
            .mapValues { it.value.size }

        return mapOf(
            "totalDonations" to records.size,
            "totalAmount" to totalAmount,
            "averageAmount" to averageAmount,
            "paymentMethodDistribution" to paymentMethodDistribution,
            "currencyDistribution" to records.groupBy { it.currency }.mapValues { it.value.size }
        )
    }
} 