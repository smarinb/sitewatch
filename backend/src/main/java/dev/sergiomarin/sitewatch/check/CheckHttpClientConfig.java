package dev.sergiomarin.sitewatch.check;

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CheckerProperties.class)
class CheckHttpClientConfig {

    static final int MAX_REDIRECTS = 5;

    @Bean(destroyMethod = "close")
    CloseableHttpClient checkHttpClient(CheckerProperties properties) throws NoSuchAlgorithmException {
        return create(properties, SSLContext.getDefault());
    }

    /** Builds the client used for checks; tests pass an SSL context that trusts their own certificate. */
    static CloseableHttpClient create(CheckerProperties properties, SSLContext sslContext) {
        Timeout connectTimeout = Timeout.of(properties.connectTimeout());
        Timeout responseTimeout = Timeout.of(properties.responseTimeout());
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new GuardedDnsResolver(properties.allowPrivateAddresses()))
                .setTlsSocketStrategy(new DefaultClientTlsStrategy(sslContext))
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(connectTimeout)
                        .setSocketTimeout(responseTimeout)
                        .build())
                .setMaxConnTotal(properties.maxConcurrent() * 2)
                .setMaxConnPerRoute(2)
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(responseTimeout)
                        .setRedirectsEnabled(true)
                        .setMaxRedirects(MAX_REDIRECTS)
                        .build())
                .setUserAgent(properties.userAgent())
                .disableCookieManagement()
                .disableAuthCaching()
                .disableAutomaticRetries()
                .evictIdleConnections(TimeValue.ofMinutes(1))
                .build();
    }
}
