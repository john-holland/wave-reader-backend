package main.kotlin.graphql

import main.kotlin.ml.MLPrediction
import main.kotlin.ml.MLSettingsService
import main.kotlin.payment.DonationRecord
import main.kotlin.payment.PaymentRequest
import main.kotlin.payment.PaymentResponse
import main.kotlin.payment.PaymentService
import main.kotlin.logging.DataDogLogger
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import java.util.concurrent.ConcurrentHashMap

class MLPaymentResolver(
    private val mlService: MLSettingsService,
    private val paymentService: PaymentService
) {
    private val donationSubscribers = ConcurrentHashMap<String, FluxSink<DonationRecord>>()

    fun predictDefaultSettings(type: String, context: Map<String, Any>): MLPrediction {
        return try {
            mlService.predictDefaultSettings(
                type = type,
                context = context,
                historicalData = emptyList() // In a real implementation, fetch from database
            )
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to predict default settings",
                mapOf(
                    "type" to type,
                    "error" to e.message
                )
            )
            throw e
        }
    }

    fun processPayment(input: PaymentRequest): PaymentResponse {
        return try {
            val response = paymentService.processPayment(input)
            
            // If payment is successful, record the donation
            if (response.status == "completed") {
                val record = paymentService.recordDonation(response)
                
                // Notify subscribers
                donationSubscribers.values.forEach { sink ->
                    sink.next(record)
                }
            }
            
            response
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to process payment",
                mapOf(
                    "error" to e.message,
                    "paymentMethod" to input.paymentMethod,
                    "amount" to input.amount
                )
            )
            throw e
        }
    }

    fun recordDonation(paymentId: String): DonationRecord {
        return try {
            val payment = paymentService.getDonationHistory(paymentMethod = null)
                .find { it.id == paymentId }
                ?: throw IllegalArgumentException("Payment not found: $paymentId")
            
            val record = paymentService.recordDonation(
                PaymentResponse(
                    id = payment.id,
                    status = payment.status,
                    amount = payment.amount,
                    currency = payment.currency,
                    paymentMethod = payment.paymentMethod,
                    metadata = payment.metadata
                )
            )
            
            // Notify subscribers
            donationSubscribers.values.forEach { sink ->
                sink.next(record)
            }
            
            record
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to record donation",
                mapOf(
                    "error" to e.message,
                    "paymentId" to paymentId
                )
            )
            throw e
        }
    }

    fun getDonationHistory(
        startTime: Long? = null,
        endTime: Long? = null,
        paymentMethod: String? = null
    ): List<DonationRecord> {
        return try {
            paymentService.getDonationHistory(startTime, endTime, paymentMethod)
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to get donation history",
                mapOf(
                    "error" to e.message,
                    "startTime" to startTime,
                    "endTime" to endTime,
                    "paymentMethod" to paymentMethod
                )
            )
            emptyList()
        }
    }

    fun getDonationStats(): Map<String, Any> {
        return try {
            paymentService.getDonationStats()
        } catch (e: Exception) {
            DataDogLogger.error(
                "Failed to get donation stats",
                mapOf("error" to e.message)
            )
            mapOf(
                "totalDonations" to 0,
                "totalAmount" to 0.0,
                "averageAmount" to 0.0,
                "paymentMethodDistribution" to emptyMap<String, Int>(),
                "currencyDistribution" to emptyMap<String, Int>()
            )
        }
    }

    fun donationProcessed(): Publisher<DonationRecord> {
        return Flux.create { sink ->
            val id = java.util.UUID.randomUUID().toString()
            donationSubscribers[id] = sink
            sink.onDispose {
                donationSubscribers.remove(id)
            }
        }
    }
} 