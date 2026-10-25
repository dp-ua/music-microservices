package com.music.resource.contract;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.music.resource.client.song.SongServiceClient;
import com.music.resource.client.song.SongServiceClientProxy;
import com.music.resource.client.song.SongServiceClientProxyImpl;

import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;

/**
 * Contract C7: resource-service asks song-service to delete the metadata, DELETE /songs?id=1,2.
 * The real Feign client and the real proxy of resource-service talk to a Pact mock server.
 */
@SpringBootTest(
        classes = SongServiceDeleteContractTest.ClientsConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.application.name=resource-service-contract",
                "eureka.client.enabled=false",
                "spring.cloud.openfeign.client.config.song-service.url=http://localhost:18083",
                "services.config.song-service-client.urls.delete-metadata=/songs"
        })
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "song-service", port = "18083", pactVersion = PactSpecVersion.V3)
class SongServiceDeleteContractTest {

    /**
     * Only the Feign client and its proxy: no database, no RabbitMQ, no Eureka.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            RabbitAutoConfiguration.class})
    @EnableFeignClients(clients = SongServiceClient.class)
    @Import(SongServiceClientProxyImpl.class)
    static class ClientsConfig {

    }

    @Autowired
    private SongServiceClientProxy songServiceClient;

    @Pact(consumer = "resource-service")
    RequestResponsePact deleteMetadata(PactDslWithProvider builder) {
        DslPart response = new PactDslJsonBody().array("ids").numberValue(1).numberValue(2).closeArray();
        return builder
                .given("metadata of songs 1 and 2 exist")
                .uponReceiving("a request to delete the metadata of songs 1 and 2")
                .path("/songs")
                .method("DELETE")
                .query("id=1,2")
                .willRespondWith()
                .status(200)
                .body(response)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "deleteMetadata")
    void deletesTheMetadataOfSeveralSongs() {
        assertThatCode(() -> songServiceClient.delete(List.of(1L, 2L))).doesNotThrowAnyException();
    }

}
