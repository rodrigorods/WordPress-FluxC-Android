package org.wordpress.android.fluxc.wc

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.yarolegovich.wellsql.WellSql
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.SingleStoreWellSqlConfigForTests
import org.wordpress.android.fluxc.TestSiteSqlUtils
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCProductSettingsModel
import org.wordpress.android.fluxc.model.WCSSRModel
import org.wordpress.android.fluxc.model.WCSettingsModel
import org.wordpress.android.fluxc.model.plugin.SitePluginModel
import org.wordpress.android.fluxc.model.settings.WCSettingsMapper
import org.wordpress.android.fluxc.network.BaseRequest.GenericErrorType.NETWORK_ERROR
import org.wordpress.android.fluxc.network.discovery.RootWPAPIRestResponse
import org.wordpress.android.fluxc.network.rest.wpcom.wc.WooCommerceRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.wc.WooError
import org.wordpress.android.fluxc.network.rest.wpcom.wc.WooErrorType.INVALID_RESPONSE
import org.wordpress.android.fluxc.network.rest.wpcom.wc.WooPayload
import org.wordpress.android.fluxc.network.rest.wpcom.wc.WooResult
import org.wordpress.android.fluxc.network.rest.wpcom.wc.system.WCApiVersionResponse
import org.wordpress.android.fluxc.network.rest.wpcom.wc.system.WCSystemPluginResponse
import org.wordpress.android.fluxc.network.rest.wpcom.wc.system.WCSystemPluginResponse.SystemPluginModel
import org.wordpress.android.fluxc.network.rest.wpcom.wc.system.WooSystemRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.wc.system.WooSystemRestClient.WPSiteSettingsResponse
import org.wordpress.android.fluxc.network.rest.wpcom.wc.system.toDomainModel
import org.wordpress.android.fluxc.persistence.WCSettingsSqlUtils.WCSettingsBuilder
import org.wordpress.android.fluxc.persistence.WellSqlConfig
import org.wordpress.android.fluxc.site.SiteUtils
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.fluxc.store.SiteStore.OnSiteChanged
import org.wordpress.android.fluxc.store.WooCommerceStore
import org.wordpress.android.fluxc.test
import org.wordpress.android.fluxc.tools.initCoroutineEngine
import org.wordpress.android.fluxc.wc.settings.WCSettingsTestUtils
import kotlin.test.assertEquals

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class WooCommerceStoreTest {
    private companion object {
        const val TEST_SITE_REMOTE_ID = 1337L
        const val SUPPORTED_API_VERSION = "wc/v3"
    }

    private val appContext = ApplicationProvider.getApplicationContext<Application>()
    private val restClient = mock<WooSystemRestClient>()
    private val siteStore = mock<SiteStore>()
    private val wcrestClient = mock<WooCommerceRestClient>()
    private val settingsMapper = WCSettingsMapper()

    private val wooCommerceStore = WooCommerceStore(
        appContext = appContext,
        dispatcher = Dispatcher(),
        coroutineEngine = initCoroutineEngine(),
        siteStore = siteStore,
        systemRestClient = restClient,
        wcCoreRestClient = wcrestClient,
        siteSqlUtils = TestSiteSqlUtils.siteSqlUtils,
        settingsMapper = settingsMapper
    )
    private val error = WooError(INVALID_RESPONSE, NETWORK_ERROR, "Invalid site ID")
    private val site = SiteModel().apply {
        id = 1
        siteId = TEST_SITE_REMOTE_ID
    }

    private val response = WCSystemPluginResponse(
        listOf(
            SystemPluginModel(
                plugin = "woocommerce-services/woocommerce-services",
                name = "WooCommerce Shipping &amp; Tax",
                version = "1.0",
                url = "url"
            ),
            SystemPluginModel(
                plugin = "other-plugin/other-plugin",
                name = "Other Plugin",
                version = "2.0",
                url = "url"
            )
        ),
        listOf(
            SystemPluginModel(plugin = "inactive", name = "Inactive", version = "1.0", url = "url")
        )
    )

    private val siteSettingsResponse = WCSettingsTestUtils.getSiteSettingsResponse()
    private val siteProductSettingsResponse = WCSettingsTestUtils.getSiteProductSettingsResponse()

    @Before
    fun setUp() {
        val config = SingleStoreWellSqlConfigForTests(
            appContext,
            listOf(
                SitePluginModel::class.java,
                SiteModel::class.java,
                WCProductSettingsModel::class.java,
                WCSettingsBuilder::class.java
            ),
            WellSqlConfig.ADDON_WOOCOMMERCE
        )
        WellSql.init(config)
        config.reset()
    }

    @Test
    fun testGetWooCommerceSites() {
        val nonWooSite = SiteModel().apply { siteId = 42 }
        WellSql.insert(nonWooSite).execute()

        assertEquals(0, wooCommerceStore.getWooCommerceSites().size)

        val wooJetpackSite = SiteModel().apply {
            siteId = 43
            hasWooCommerce = true
            setIsWPCom(false)
        }
        WellSql.insert(wooJetpackSite).execute()

        assertEquals(1, wooCommerceStore.getWooCommerceSites().size)

        val wooAtomicSite = SiteModel().apply {
            siteId = 44
            hasWooCommerce = true
            setIsWPCom(true)
        }
        WellSql.insert(wooAtomicSite).execute()

        assertEquals(2, wooCommerceStore.getWooCommerceSites().size)
    }

    @Test
    fun `when fetching plugin fails, then error returned`() = test {
        val result = getPlugin(isError = true)

        Assertions.assertThat(result.error).isEqualTo(error)
    }

    @Test
    fun `when fetching plugin succeeds, then success returned`() = test {
        val result = getPlugin(isError = false)

        Assertions.assertThat(result.isError).isFalse
        Assertions.assertThat(result.model).isNotNull
    }

    @Test
    fun `when fetching plugin succeeds, then plugins inserted into db`() = test {
        getPlugin(isError = false)
        val expectedModel = response.plugins.mapIndexed { index, model ->
            model.toDomainModel(site.id).apply { id = index + 1 }
        }

        val result = wooCommerceStore.getSitePlugins(site)

        Assertions.assertThat(result)
            .hasSameSizeAs(expectedModel)
            .allMatch { model ->
                expectedModel.any { model.id == it.id && model.name == it.name && model.isActive == it.isActive }
            }
    }

    @Test
    fun `when fetching ssr fails, then error returned`() = test {
        val result = fetchSSR(isError = true)

        Assertions.assertThat(result.error).isEqualTo(error)
    }

    @Test
    fun `when fetching ssr succeeds, then success returned`() = test {
        val result = fetchSSR(isError = false)

        Assertions.assertThat(result.isError).isFalse
        Assertions.assertThat(result.model).isNotNull
    }

    @Test
    fun `when fetch site settings succeeds, then success returned`() = test {
        val result: WooResult<WCSettingsModel> = fetchSiteSettings()

        Assertions.assertThat(result.isError).isFalse
        Assertions.assertThat(result.model).isNotNull
        Assertions.assertThat(result.model).isEqualTo(
            settingsMapper.mapSiteSettings(siteSettingsResponse!!, site)
        )
    }

    @Test
    fun `when fetch site settings fails, then error returned`() {
        runBlocking {
            val result: WooResult<WCSettingsModel> = fetchSiteSettings(isError = true)
            Assertions.assertThat(result.error).isEqualTo(error)
            Assertions.assertThat(result.model).isNull()
        }
    }

    @Test
    fun `when fetch site product settings succeeds, then success returned`() {
        runBlocking {
            val expectedModel = settingsMapper.mapProductSettings(siteProductSettingsResponse!!, site)

            val result: WooResult<WCProductSettingsModel> = fetchSiteProductSettings()

            Assertions.assertThat(result.isError).isFalse
            Assertions.assertThat(result.model).isNotNull
            Assertions.assertThat(result.model?.localSiteId).isEqualTo(expectedModel.localSiteId)
            Assertions.assertThat(result.model?.weightUnit).isEqualTo(expectedModel.weightUnit)
            Assertions.assertThat(result.model?.dimensionUnit).isEqualTo(expectedModel.dimensionUnit)
        }
    }

    @Test
    fun `when fetch site product settings fails, then error returned`() {
        runBlocking {
            val result: WooResult<WCProductSettingsModel> = fetchSiteProductSettings(isError = true)
            Assertions.assertThat(result.error).isEqualTo(error)
            Assertions.assertThat(result.model).isNull()
        }
    }

    @Test
    fun `when fetching supported api version succeeds, then success returned`() {
        runBlocking {
            val result: WooResult<WCApiVersionResponse> = fetchSupportedWooApiVersion(
                response = WCSettingsTestUtils.getSupportedApiVersionResponse()
            )

            Assertions.assertThat(result.isError).isFalse
            Assertions.assertThat(result.model).isNotNull
            Assertions.assertThat(result.model?.apiVersion).isEqualTo(SUPPORTED_API_VERSION)
            Assertions.assertThat(result.model?.siteModel).isEqualTo(site)
        }
    }

    @Test
    fun `when fetching unsupported api version succeeds, then blank api version returned`() {
        runBlocking {
            val result: WooResult<WCApiVersionResponse> = fetchSupportedWooApiVersion(
                response = RootWPAPIRestResponse()
            )

            Assertions.assertThat(result.isError).isFalse
            Assertions.assertThat(result.model).isNotNull
            Assertions.assertThat(result.model?.apiVersion).isBlank
        }
    }

    @Test
    fun `when fetching supported api version fails, then error returned`() {
        runBlocking {
            val result: WooResult<WCApiVersionResponse> = fetchSupportedWooApiVersion(
                isError = true,
                response = WCSettingsTestUtils.getUnsupportedApiVersionResponse()
            )
            Assertions.assertThat(result.error).isEqualTo(error)
        }
    }

    @Test
    fun `when fetching a jetpack cp site, then fetch metadata from the remote site manually`() {
        runBlocking {
            val site = SiteUtils.generateJetpackCPSite()
            whenever(siteStore.fetchSites(any())).thenReturn(OnSiteChanged(1, updatedSites = listOf(site)))
            whenever(siteStore.sites).thenReturn(listOf(site))
            whenever(restClient.fetchSiteSettings(site)).thenReturn(
                WooPayload(
                    WPSiteSettingsResponse(title = "new title")
                )
            )
            whenever(restClient.checkIfWooCommerceIsAvailable(site)).thenReturn(WooPayload(true))

            val sites = wooCommerceStore.fetchWooCommerceSites().model!!

            verify(restClient).fetchSiteSettings(site)
            verify(restClient).checkIfWooCommerceIsAvailable(site)
            Assertions.assertThat(sites.first().hasWooCommerce).isTrue
            Assertions.assertThat(sites.first().name).isEqualTo("new title")
        }
    }

    @Test
    fun `when enabling coupons succeeds, then true is returned`() {
        runBlocking {
            whenever(wcrestClient.enableCoupons(site)).thenReturn(WooPayload(true))
            val result = wooCommerceStore.enableCoupons(site)
            Assertions.assertThat(result).isTrue
        }
    }

    @Test
    fun `when enabling coupons fails, then false is returned`() {
        runBlocking {
            whenever(wcrestClient.enableCoupons(site)).thenReturn(WooPayload(false))
            val result = wooCommerceStore.enableCoupons(site)
            Assertions.assertThat(result).isFalse
        }
    }

    private suspend fun getPlugin(isError: Boolean = false): WooResult<List<SitePluginModel>> {
        val payload = WooPayload(response)
        if (isError) {
            whenever(restClient.fetchInstalledPlugins(any())).thenReturn(WooPayload(error))
        } else {
            whenever(restClient.fetchInstalledPlugins(any())).thenReturn(payload)
        }
        return wooCommerceStore.fetchSitePlugins(site)
    }

    private suspend fun fetchSSR(isError: Boolean = false): WooResult<WCSSRModel> {
        val payload = WooPayload(WCSettingsTestUtils.getSSRResponse())
        if (isError) {
            whenever(restClient.fetchSSR(any())).thenReturn(WooPayload(error))
        } else {
            whenever(restClient.fetchSSR(any())).thenReturn(payload)
        }
        return wooCommerceStore.fetchSSR(site)
    }

    private suspend fun fetchSupportedWooApiVersion(
        isError: Boolean = false,
        response: RootWPAPIRestResponse
    ): WooResult<WCApiVersionResponse> {
        val payload = WooPayload(response)
        if (isError) {
            whenever(wcrestClient.fetchSupportedWooApiVersion(any(), any())).thenReturn(WooPayload(error))
        } else {
            whenever(wcrestClient.fetchSupportedWooApiVersion(any(), any())).thenReturn(payload)
        }
        return wooCommerceStore.fetchSupportedApiVersion(site)
    }

    private suspend fun fetchSiteSettings(isError: Boolean = false): WooResult<WCSettingsModel> {
        val payload = WooPayload(siteSettingsResponse)
        if (isError) {
            whenever(wcrestClient.fetchSiteSettingsGeneral(site)).thenReturn(WooPayload(error))
        } else {
            whenever(wcrestClient.fetchSiteSettingsGeneral(site)).thenReturn(payload)
        }
        return wooCommerceStore.fetchSiteGeneralSettings(site)
    }

    private suspend fun fetchSiteProductSettings(isError: Boolean = false): WooResult<WCProductSettingsModel> {
        val payload = WooPayload(siteProductSettingsResponse)
        if (isError) {
            whenever(wcrestClient.fetchSiteSettingsProducts(site)).thenReturn(WooPayload(error))
        } else {
            whenever(wcrestClient.fetchSiteSettingsProducts(site)).thenReturn(payload)
        }
        return wooCommerceStore.fetchSiteProductSettings(site)
    }
}
