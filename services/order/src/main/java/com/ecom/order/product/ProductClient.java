package com.ecom.order.product;

import com.ecom.order.exception.RemoteErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * RestClient: the same kind of call as CustomerClient, written by hand, to compare with Feign.
 * The LoadBalancerInterceptor turns http://product-service/... into a real host:port from Eureka.
 */
@Component
public class ProductClient {

    private static final String SERVICE = "product-service";

    private final RestClient restClient;
    private final RemoteErrors remoteErrors;

    public ProductClient(RestClient.Builder builder,
                         LoadBalancerClient loadBalancerClient,
                         RemoteErrors remoteErrors,
                         @Value("${application.config.product-url}") String productUrl) {
        this.restClient = builder
                .baseUrl(productUrl)
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient))
                .build();
        this.remoteErrors = remoteErrors;
    }

    public List<PurchaseResponse> purchaseProducts(List<PurchaseRequest> requests) {
        try {
            return restClient.post()
                    .uri("/purchase")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requests)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw remoteErrors.toBusinessException(
                                SERVICE, response.getStatusCode().value(), response.getBody().readAllBytes());
                    })
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (ResourceAccessException | IllegalStateException e) {
            // Connection refused, timeout, or "No instances available" from the load balancer.
            throw remoteErrors.unavailable(SERVICE);
        }
    }
}
