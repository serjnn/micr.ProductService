package com.serjnn.ProductService.externaltask.client;

import com.serjnn.ProductService.externaltask.config.ExternalTaskProperties;
import com.serjnn.ProductService.externaltask.dto.ExternalServiceRequest;
import com.serjnn.ProductService.externaltask.dto.ExternalServiceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class ExternalServiceClient {

    private final RestClient restClient;
    private final ExternalTaskProperties properties;

    public ExternalServiceClient(@Qualifier("externalTaskRestClient") RestClient restClient,
                                 ExternalTaskProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private String getNormalizedBaseUrl() {
        String url = properties.serviceUrl();
        if (url == null) {
            return "";
        }
        return url.replaceAll("/+$", "");
    }

    public ExternalServiceResponse postTask(UUID businessKey, String taskType, String payload) {
        log.info("Sending POST for external task: businessKey={}, taskType={}", businessKey, taskType);
        return restClient.post()
                .uri(getNormalizedBaseUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExternalServiceRequest(businessKey, taskType, payload))
                .retrieve()
                .body(ExternalServiceResponse.class);
    }

    public Optional<ExternalServiceResponse> checkTaskStatus(UUID businessKey) {
        log.info("Sending GET audit inquiry for external task: businessKey={}", businessKey);
        try {
            ExternalServiceResponse response = restClient.get()
                    .uri(getNormalizedBaseUrl() + "/by-reference/{businessKey}", businessKey)
                    .retrieve()
                    .body(ExternalServiceResponse.class);
            return Optional.ofNullable(response);
        } catch (HttpClientErrorException.NotFound e) {
            log.info("External task audit returned 404 Not Found: businessKey={}", businessKey);
            return Optional.empty();
        }
    }
}
