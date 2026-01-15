package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ErrorModel
import com.coulterpeterson.floatnative.openapi.models.PaymentAddressModel
import com.coulterpeterson.floatnative.openapi.models.PaymentInvoiceListV2Response
import com.coulterpeterson.floatnative.openapi.models.PaymentMethodModel

interface PaymentsV2Api {
    /**
     * POST api/v2/payment/address/add
     * Add Address
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/payment/address/add")
    suspend fun addAddress(): Response<kotlin.Any>

    /**
     * POST api/v2/payment/method/add
     * Add Payment Method
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/payment/method/add")
    suspend fun addPaymentMethod(): Response<kotlin.Any>

    /**
     * POST api/v2/payment/subscription/cancel
     * Cancel Subscription
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/payment/subscription/cancel")
    suspend fun cancelSubscription(): Response<kotlin.Any>

    /**
     * POST api/v2/payment/address/delete
     * Delete Address
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/payment/address/delete")
    suspend fun deleteAddress(): Response<kotlin.Any>

    /**
     * POST api/v2/payment/method/delete
     * Delete Payment Method
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/payment/method/delete")
    suspend fun deletePaymentMethod(): Response<kotlin.Any>

    /**
     * GET api/v2/payment/tax/estimate
     * Estimate Taxes
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @GET("api/v2/payment/tax/estimate")
    suspend fun estimateTaxes(): Response<kotlin.Any>

    /**
     * GET api/v2/payment/address/list
     * List Addresses
     * Retrieve a list of billing addresses saved to the user&#39;s account, to be used in conjunction with a payment method when purchasing subscriptions to creators.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.collections.List<PaymentAddressModel>]
     */
    @GET("api/v2/payment/address/list")
    suspend fun listAddresses(): Response<kotlin.collections.List<PaymentAddressModel>>

    /**
     * GET api/v2/payment/invoice/list
     * List Invoices
     * Retrieve a list of paid or unpaid subscription invoices for the user.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [PaymentInvoiceListV2Response]
     */
    @GET("api/v2/payment/invoice/list")
    suspend fun listInvoices(): Response<PaymentInvoiceListV2Response>

    /**
     * GET api/v2/payment/method/list
     * List Payment Methods
     * Retrieve a list of saved payment methods for the user&#39;s account. Payment methods are how the user can pay for their subscription to creators on the platform.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.collections.List<PaymentMethodModel>]
     */
    @GET("api/v2/payment/method/list")
    suspend fun listPaymentMethods(): Response<kotlin.collections.List<PaymentMethodModel>>

    /**
     * POST api/v2/payment/subscription/purchase
     * Purchase Subscription
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/payment/subscription/purchase")
    suspend fun purchaseSubscription(): Response<kotlin.Any>

    /**
     * POST api/v2/payment/address/set
     * Set Primary Address
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/payment/address/set")
    suspend fun setPrimaryAddress(): Response<kotlin.Any>

    /**
     * POST api/v2/payment/method/set
     * Set Primary Payment Method
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/payment/method/set")
    suspend fun setPrimaryPaymentMethod(): Response<kotlin.Any>

    /**
     * POST api/v2/payment/address/update
     * Update Address
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/payment/address/update")
    suspend fun updateAddress(): Response<kotlin.Any>

    /**
     * POST api/v2/payment/webhook/{paymentProcessor}
     * Webhook
     * TODO - Not used in Floatplane code.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param paymentProcessor 
     * @return [kotlin.Any]
     */
    @Deprecated("This api was deprecated")
    @POST("api/v2/payment/webhook/{paymentProcessor}")
    suspend fun webhook(@Path("paymentProcessor") paymentProcessor: kotlin.String): Response<kotlin.Any>

    /**
     * POST api/v2/payment/webhook/{paymentProcessor}/{subPaymentProcessor}
     * Webhook With Subprocessor
     * TODO - Not used in Floatplane code.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param paymentProcessor 
     * @param subPaymentProcessor 
     * @return [kotlin.Any]
     */
    @Deprecated("This api was deprecated")
    @POST("api/v2/payment/webhook/{paymentProcessor}/{subPaymentProcessor}")
    suspend fun webhookWithSubprocessor(@Path("paymentProcessor") paymentProcessor: kotlin.String, @Path("subPaymentProcessor") subPaymentProcessor: kotlin.String): Response<kotlin.Any>

}
