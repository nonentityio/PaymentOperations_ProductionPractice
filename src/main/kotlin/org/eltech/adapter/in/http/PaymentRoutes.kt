package org.eltech.adapter.`in`.http

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.eltech.application.port.`in`.CreatePaymentUseCase
import org.eltech.application.port.`in`.GetPaymentUseCase
import org.eltech.application.port.`in`.ListPaymentsUseCase
import org.eltech.application.port.`in`.CancelPaymentUseCase
import org.eltech.infrastructure.validation.PaymentIds

class PaymentRoutes(
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val getPaymentUseCase: GetPaymentUseCase,
    private val listPaymentsUseCase: ListPaymentsUseCase,
    private val cancelPaymentUseCase: CancelPaymentUseCase,
    private val authHandler: AuthHandler
) {
    fun mount(router: Router) {
        router.post("/payments").handler(authHandler::requireBearerToken).handler(::createPayment)
        router.get("/payments").handler(authHandler::requireBearerToken).handler(::listPayments)
        router.get("/payments/:paymentId").handler(authHandler::requireBearerToken).handler(::getPayment)
        router.post("/payments/:paymentId/cancel").handler(authHandler::requireBearerToken).handler(::cancelPayment)
    }

    private fun cancelPayment(ctx: RoutingContext) {
        val paymentId = PaymentIds.parseOrNull(ctx.pathParam("paymentId"))
        if (paymentId == null) {
            fail(ctx, 400, "invalid paymentId")
            return
        }

        cancelPaymentUseCase.cancelPayment(paymentId)
            .onSuccess { ok(ctx, JsonObject().put("status", "CANCELLED")) }
            .onFailure {
                if (it is IllegalArgumentException) {
                    fail(ctx, 409, it.message ?: "payment cannot be cancelled")
                } else {
                    fail(ctx, 500, it.message ?: "payment cancellation failed")
                }
            }
    }

    private fun createPayment(ctx: RoutingContext) {
        val command = try {
            val request = CreatePaymentHttpRequest.from(ctx)
            request.toCommand()
        } catch (error: ArithmeticException) {
            fail(ctx, 400, "amount must have no more than two decimal places")
            return
        } catch (error: IllegalArgumentException) {
            fail(ctx, 400, error.message ?: "invalid payment request")
            return
        }

        createPaymentUseCase.createPayment(command)
            .onSuccess { result ->
                val response = createPaymentResultToJson(result)
                if (result.idempotentReplay) ok(ctx, response)
                else ctx.response()
                    .setStatusCode(202)
                    .putHeader("content-type", "application/json")
                    .end(response.encode())
            }
            .onFailure {
                if (it is IllegalArgumentException && it.message?.contains("Idempotency-Key") == true) {
                    fail(ctx, 409, it.message ?: "idempotency conflict")
                } else if (it is IllegalArgumentException) {
                    fail(ctx, 400, it.message ?: "invalid payment request")
                } else {
                    fail(ctx, 500, it.message ?: "payment creation failed")
                }
            }
    }

    private fun getPayment(ctx: RoutingContext) {
        val paymentId = PaymentIds.parseOrNull(ctx.pathParam("paymentId"))
        if (paymentId == null) {
            fail(ctx, 400, "invalid paymentId")
            return
        }

        getPaymentUseCase.getPayment(paymentId)
            .onSuccess { details -> ok(ctx, paymentDetailsToJson(details)) }
            .onFailure { fail(ctx, 404, it.message ?: "payment not found") }
    }

    private fun listPayments(ctx: RoutingContext) {
        val clientId = ctx.queryParam("clientId").firstOrNull()
        listPaymentsUseCase.listPayments(clientId)
            .onSuccess { items -> ok(ctx, JsonObject().put("items", items.map(::paymentToJson))) }
            .onFailure { fail(ctx, 500, it.message ?: "failed to list payments") }
    }
}
