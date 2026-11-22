package com.bian.hub.infrastructure.fineract;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.*;
import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;
import java.util.Base64;

@Configuration
public class FineractClientConfig {

    @Bean
    public RestClient fineractRestClient(
            @Value("${fineract.base-url}") String baseUrl,
            @Value("${fineract.tenant-id}") String tenantId,
            @Value("${fineract.username}") String username,
            @Value("${fineract.password}") String password,
            @Value("${fineract.trust-self-signed:true}") boolean trustSelfSigned) throws Exception {

        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Fineract-Platform-TenantId", tenantId)
                .defaultHeader("Authorization", basicAuth)
                .defaultHeader("Accept", "application/json");

        if (trustSelfSigned) {
            builder.requestFactory(createInsecureRequestFactory());
        }

        return builder.build();
    }

    private SimpleClientHttpRequestFactory createInsecureRequestFactory() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        return new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                if (connection instanceof HttpsURLConnection httpsConn) {
                    httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());
                    httpsConn.setHostnameVerifier((hostname, session) -> true);
                }
                super.prepareConnection(connection, httpMethod);
            }
        };
    }
}
