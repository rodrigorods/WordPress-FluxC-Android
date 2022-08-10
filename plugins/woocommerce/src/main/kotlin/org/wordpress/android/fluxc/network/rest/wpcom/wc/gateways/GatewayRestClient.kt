package org.wordpress.android.fluxc.network.rest.wpcom.wc.gateways

import android.content.Context
import com.android.volley.RequestQueue
import com.google.gson.annotations.SerializedName
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.endpoint.WOOCOMMERCE
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.rest.wpcom.BaseWPComRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken
import org.wordpress.android.fluxc.network.rest.wpcom.jetpacktunnel.JetpackTunnelGsonRequestBuilder
import org.wordpress.android.fluxc.network.rest.wpcom.jetpacktunnel.JetpackTunnelGsonRequestBuilder.JetpackResponse.JetpackError
import org.wordpress.android.fluxc.network.rest.wpcom.jetpacktunnel.JetpackTunnelGsonRequestBuilder.JetpackResponse.JetpackSuccess
import org.wordpress.android.fluxc.network.rest.wpcom.wc.WooPayload
import org.wordpress.android.fluxc.network.rest.wpcom.wc.toWooError
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class GatewayRestClient @Inject constructor(
    dispatcher: Dispatcher,
    private val jetpackTunnelGsonRequestBuilder: JetpackTunnelGsonRequestBuilder,
    appContext: Context?,
    @Named("regular") requestQueue: RequestQueue,
    accessToken: AccessToken,
    userAgent: UserAgent
) : BaseWPComRestClient(appContext, dispatcher, requestQueue, accessToken, userAgent) {
    suspend fun fetchGateway(
        site: SiteModel,
        gatewayId: String
    ): WooPayload<GatewayResponse> {
        val url = WOOCOMMERCE.payment_gateways.gateway(gatewayId).pathV3

        val response = jetpackTunnelGsonRequestBuilder.syncGetRequest(
                this,
                site,
                url,
                emptyMap(),
                GatewayResponse::class.java
        )
        return when (response) {
            is JetpackSuccess -> {
                WooPayload(response.data)
            }
            is JetpackError -> {
                WooPayload(response.error.toWooError())
            }
        }
    }

    suspend fun postPaymentGateway(
        site: SiteModel,
        gatewayId: GatewayId,
        enabled: Boolean,
    ): WooPayload<GatewayResponse> {
        val url = WOOCOMMERCE.payment_gateways.gateway(gatewayId.toString()).pathV3
        val params = mapOf("enabled" to enabled)
        val response = jetpackTunnelGsonRequestBuilder.syncPostRequest(
            this,
            site,
            url,
            params,
            GatewayResponse::class.java
        )

        return when (response) {
            is JetpackSuccess -> {
                WooPayload(response.data)
            }
            is JetpackError -> {
                WooPayload(response.error.toWooError())
            }
        }
    }

    suspend fun postCashOnDeliveryTitle(
        site: SiteModel,
        title: String
    ): WooPayload<GatewayResponse> {
        val url = WOOCOMMERCE.payment_gateways.gateway(title).pathV3
        val params = mapOf("Cash on Delivery" to title)
        val response = jetpackTunnelGsonRequestBuilder.syncPostRequest(
            this,
            site,
            url,
            params,
            GatewayResponse::class.java
        )

        return when (response) {
            is JetpackSuccess -> {
                WooPayload(response.data)
            }
            is JetpackError -> {
                WooPayload(response.error.toWooError())
            }
        }
    }

    suspend fun fetchAllGateways(
        site: SiteModel
    ): WooPayload<Array<GatewayResponse>> {
        val url = WOOCOMMERCE.payment_gateways.pathV3

        val response = jetpackTunnelGsonRequestBuilder.syncGetRequest(
                this,
                site,
                url,
                emptyMap(),
                Array<GatewayResponse>::class.java
        )
        return when (response) {
            is JetpackSuccess -> {
                WooPayload(response.data)
            }
            is JetpackError -> {
                WooPayload(response.error.toWooError())
            }
        }
    }

    data class GatewayResponse(
        @SerializedName("id") val gatewayId: String,
        @SerializedName("title") val title: String?,
        @SerializedName("description") val description: String?,
        @SerializedName("order") val order: String?,
        @SerializedName("enabled") val enabled: Boolean?,
        @SerializedName("method_title") val methodTitle: String?,
        @SerializedName("method_description") val methodDescription: String?,
        @SerializedName("method_supports") val features: List<String>?
    )

    enum class GatewayId {
        COD
    }
}
