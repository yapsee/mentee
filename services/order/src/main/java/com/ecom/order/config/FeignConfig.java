package com.ecom.order.config;

import com.ecom.order.exception.RemoteErrors;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FeignConfig {

    /** Every Feign client: an error status becomes a BusinessException instead of a generic FeignException. */
    @Bean
    ErrorDecoder errorDecoder(RemoteErrors remoteErrors) {
        return (methodKey, response) -> remoteErrors.toBusinessException(
                serviceName(response), response.status(), readBody(response));
    }

    private static String serviceName(Response response) {
        return response.request().requestTemplate().feignTarget().name();
    }

    private static byte[] readBody(Response response) {
        if (response.body() == null) {
            return new byte[0];
        }
        try (InputStream in = response.body().asInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
