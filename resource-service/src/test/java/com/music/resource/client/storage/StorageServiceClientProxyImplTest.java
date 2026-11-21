package com.music.resource.client.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;

/**
 * The circuit breaker around storage-service with the real Resilience4j and Spring AOP, the Feign client is a mock.
 * Checks the fallback (stub data with the structure of the real response) and the opening of the circuit.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = StorageServiceClientProxyImplTest.TestConfig.class)
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.storage-service.sliding-window-size=4",
        "resilience4j.circuitbreaker.instances.storage-service.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.instances.storage-service.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.storage-service.wait-duration-in-open-state=1h",
        "storage.stub.staging.id=1",
        "storage.stub.staging.storage-type=STAGING",
        "storage.stub.staging.bucket=stub-staging",
        "storage.stub.staging.path=/files",
        "storage.stub.permanent.id=2",
        "storage.stub.permanent.storage-type=PERMANENT",
        "storage.stub.permanent.bucket=stub-permanent",
        "storage.stub.permanent.path=/files"
})
class StorageServiceClientProxyImplTest {

    @Configuration
    @ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class})
    @org.springframework.boot.context.properties.EnableConfigurationProperties(StorageStubProperties.class)
    static class TestConfig {

        @Bean
        StorageServiceClient storageServiceClient() {
            return mock(StorageServiceClient.class);
        }

        @Bean
        StorageServiceClientProxy storageServiceClientProxy(StorageServiceClient client, StorageStubProperties stub) {
            return new StorageServiceClientProxyImpl(client, stub);
        }

    }

    @Autowired
    private StorageServiceClientProxy proxy;
    @Autowired
    private StorageServiceClient client;
    @Autowired
    private CircuitBreakerRegistry registry;

    @Test
    @DisplayName("Fallback and opening of the circuit: stub data while storage-service is down, no calls when open")
    void fallbackAndOpenCircuit() {
        var breaker = registry.circuitBreaker(StorageServiceClientProxyImpl.CIRCUIT_BREAKER);
        breaker.reset();
        var real = List.of(new StorageDto(10L, StorageType.STAGING, "real-staging", "/real"));

        // storage-service is up: the real answer
        when(client.getStorages()).thenReturn(real);
        assertThat(proxy.getStorages()).isEqualTo(real);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // storage-service is down: the stub with the same structure, the circuit opens after the failures
        when(client.getStorages()).thenThrow(mock(FeignException.class));
        var stub = proxy.getStorages();
        assertThat(stub).extracting(StorageDto::getStorageType)
                .containsExactly(StorageType.STAGING, StorageType.PERMANENT);
        assertThat(stub.get(0).getBucket()).isEqualTo("stub-staging");
        assertThat(stub.get(1).getBucket()).isEqualTo("stub-permanent");
        proxy.getStorages();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // the circuit is open: the call is not even made, the stub is returned at once
        var callsBefore = org.mockito.Mockito.mockingDetails(client).getInvocations().size();
        assertThat(proxy.getStorages()).isEqualTo(stub);
        assertThat(org.mockito.Mockito.mockingDetails(client).getInvocations()).hasSize(callsBefore);
        assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls()).isPositive();
    }

}
