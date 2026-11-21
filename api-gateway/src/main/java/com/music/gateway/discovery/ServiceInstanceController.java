package com.music.gateway.discovery;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shows what the service registry (Eureka) knows. The gateway is the only component with a published port,
 * so it is the place to look at the registered instances of any service.
 */
@RestController
public class ServiceInstanceController {

    private final ReactiveDiscoveryClient discoveryClient;

    public ServiceInstanceController(ReactiveDiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    /** All services registered in Eureka with the number of their instances, e.g. {"song-service": 2}. */
    @GetMapping(value = "/service-instances", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Long>> services() {
        return discoveryClient.getServices()
                .flatMap(name -> discoveryClient.getInstances(name).count().map(count -> Map.entry(name, count)))
                .collectSortedList(Map.Entry.comparingByKey())
                .map(entries -> {
                    Map<String, Long> result = new LinkedHashMap<>();
                    entries.forEach(e -> result.put(e.getKey(), e.getValue()));
                    return result;
                });
    }

    /** All registered instances of one service, e.g. {@code /service-instances/song-service}. */
    @GetMapping(value = "/service-instances/{applicationName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ServiceInstanceInfo> serviceInstancesByApplicationName(@PathVariable String applicationName) {
        return discoveryClient.getInstances(applicationName).map(ServiceInstanceInfo::from);
    }

    public record ServiceInstanceInfo(String serviceId, String instanceId, String host, int port, String uri) {

        static ServiceInstanceInfo from(ServiceInstance instance) {
            return new ServiceInstanceInfo(instance.getServiceId(), instance.getInstanceId(),
                    instance.getHost(), instance.getPort(), instance.getUri().toString());
        }
    }
}
