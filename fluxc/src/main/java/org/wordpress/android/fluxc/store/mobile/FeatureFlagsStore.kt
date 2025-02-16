package org.wordpress.android.fluxc.store.mobile

import org.wordpress.android.fluxc.network.rest.wpcom.mobile.FeatureFlagsError
import org.wordpress.android.fluxc.network.rest.wpcom.mobile.FeatureFlagsErrorType.GENERIC_ERROR
import org.wordpress.android.fluxc.network.rest.wpcom.mobile.FeatureFlagsRestClient
import org.wordpress.android.fluxc.store.Store
import org.wordpress.android.fluxc.tools.CoroutineEngine
import org.wordpress.android.util.AppLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureFlagsStore @Inject constructor(
    private val featureFlagsRestClient: FeatureFlagsRestClient,
    private val coroutineEngine: CoroutineEngine
) {
    suspend fun fetchFeatureFlags(buildNumber: String,
        deviceId: String,
        identifier: String,
        marketingVersion: String,
        platform: String
    ) = coroutineEngine.withDefaultContext(AppLog.T.API, this, "fetch feature-flags") {
        val payload = featureFlagsRestClient.fetchFeatureFlags(
            buildNumber,
            deviceId,
            identifier,
            marketingVersion,
            platform
        )
        return@withDefaultContext when {
            payload.isError -> FeatureFlagsResult(payload.error)
            payload.featureFlags != null -> FeatureFlagsResult(payload.featureFlags)
            else -> FeatureFlagsResult(FeatureFlagsError(GENERIC_ERROR))
        }
    }

    data class FeatureFlagsResult(
        val featureFlags: Map<String, Boolean>? = null
    ) : Store.OnChanged<FeatureFlagsError>() {
        constructor(error: FeatureFlagsError) : this() {
            this.error = error
        }
    }
}
