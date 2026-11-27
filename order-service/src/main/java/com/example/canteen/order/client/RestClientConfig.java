package com.example.canteen.order.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** One RestClient per downstream service, each with a base URL and timeouts. */
@Configuration
@EnableConfigurationProperties(DownstreamProperties.class)
class RestClientConfig {

    @Bean
    RestClient menuRestClient(RestClient.Builder builder, DownstreamProperties props) {
        return builder.baseUrl(props.menuServiceUrl()).requestFactory(withTimeouts(props)).build();
    }

    @Bean
    RestClient customerRestClient(RestClient.Builder builder, DownstreamProperties props) {
        return builder.baseUrl(props.customerServiceUrl()).requestFactory(withTimeouts(props)).build();
    }

    /**
     * Never call another service without timeouts. Without them, one hung service
     * would hang every request thread of ours that is waiting for it.
     */
    private static ClientHttpRequestFactory withTimeouts(DownstreamProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout()); // max time to open the connection
        factory.setReadTimeout(props.readTimeout());       // max time to wait for the response
        return factory;
    }
}
