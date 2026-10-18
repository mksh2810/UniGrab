package com.unigrab.app

import com.unigrab.app.data.download.AutoProxyResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoProxyResolverTest {

    @Test
    fun testGetProxy() = runBlocking {
        // Run getProxy to fetch and test proxies
        val proxy = AutoProxyResolver.getProxy("https://www.google.com", forceRefresh = true)
        System.out.println("Fetched proxy: $proxy")
        if (proxy != null) {
            assertTrue(proxy.startsWith("socks5://") || proxy.startsWith("socks4://") || proxy.startsWith("http://"))
        }
    }
}
