package com.music.processor.component;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Steps of metadata-processing.feature. The event is sent to a real RabbitMQ, the real listener of the application
 * receives it, and everything the service does to its neighbours is checked on the WireMock servers.
 */
public class MetadataProcessingSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    /**
     * How long "never" and "exactly once" are watched: a late extra call would show up in this time.
     */
    private static final Duration QUIET_PERIOD = Duration.ofSeconds(3);
    private static final String SONGS_URL = "/songs";
    private static final Pattern QUEUE_COUNTS = Pattern.compile("(\\d+)\\s+(\\d+)");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${messaging.resource-uploaded.queue}")
    private String queue;

    @Before
    public void cleanUp() {
        ComponentTestEnvironment.RESOURCE_SERVICE.resetAll();
        ComponentTestEnvironment.SONG_SERVICE.resetAll();
        // nothing from a previous scenario may be left in the queue
        while (rabbitTemplate.receive(queue) != null) {
            // drained
        }
    }

    // ---------- Given ----------

    @Given("resource service stores the file {string} as resource {long}")
    public void resourceServiceStoresFile(String fileName, long resourceId) throws IOException {
        ComponentTestEnvironment.RESOURCE_SERVICE.stubFor(get(urlEqualTo("/resources/" + resourceId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "audio/mpeg")
                        .withBody(readFile(fileName))));
    }

    @Given("resource service has no resource {long}")
    public void resourceServiceHasNoResource(long resourceId) {
        ComponentTestEnvironment.RESOURCE_SERVICE.stubFor(get(urlEqualTo("/resources/" + resourceId))
                .willReturn(aResponse().withStatus(404).withBody("Resource not found")));
    }

    @Given("song service accepts new metadata")
    public void songServiceAccepts() {
        ComponentTestEnvironment.SONG_SERVICE.stubFor(post(SONGS_URL)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1}")));
    }

    @Given("song service rejects the metadata with status {int}")
    public void songServiceRejects(int status) {
        ComponentTestEnvironment.SONG_SERVICE.stubFor(post(SONGS_URL)
                .willReturn(aResponse().withStatus(status).withBody("Validation error")));
    }

    @Given("song service answers {int} because the metadata already exists")
    public void songServiceAnswersConflict(int status) {
        ComponentTestEnvironment.SONG_SERVICE.stubFor(post(SONGS_URL)
                .willReturn(aResponse().withStatus(status).withBody("Metadata already exists")));
    }

    @Given("song service answers with {int} twice and then accepts the metadata")
    public void songServiceFailsTwiceThenAccepts(int status) {
        var server = ComponentTestEnvironment.SONG_SERVICE;
        // WireMock scenario: every call moves to the next state, the third call gets 200
        server.stubFor(post(SONGS_URL).inScenario("flaky song service")
                .whenScenarioStateIs(Scenario.STARTED).willSetStateTo("second call")
                .willReturn(aResponse().withStatus(status)));
        server.stubFor(post(SONGS_URL).inScenario("flaky song service")
                .whenScenarioStateIs("second call").willSetStateTo("third call")
                .willReturn(aResponse().withStatus(status)));
        server.stubFor(post(SONGS_URL).inScenario("flaky song service")
                .whenScenarioStateIs("third call")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1}")));
    }

    // ---------- When ----------

    @When("the event {string} with resource id {long} arrives")
    public void eventArrives(String eventName, long resourceId) {
        assertThat(eventName).isEqualTo("resource uploaded");
        // the raw JSON that resource-service publishes, not our own event class: this checks the wire format
        var message = MessageBuilder
                .withBody(("{\"resourceId\":" + resourceId + "}").getBytes(UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send(queue, message);
    }

    // ---------- Then ----------

    @Then("song service receives the metadata of resource {long}")
    public void songServiceReceivesMetadata(long resourceId, Map<String, String> expectedFields) {
        var request = postRequestedFor(urlEqualTo(SONGS_URL))
                .withRequestBody(matchingJsonPath("$.id", equalTo(String.valueOf(resourceId))));
        for (var field : expectedFields.entrySet()) {
            request.withRequestBody(matchingJsonPath("$." + field.getKey(), equalTo(field.getValue())));
        }

        await().atMost(TIMEOUT).untilAsserted(() ->
                ComponentTestEnvironment.SONG_SERVICE.verify(1, request));
    }

    @Then("song service is called exactly once for resource {long}")
    public void songServiceCalledOnce(long resourceId) {
        var request = postRequestedFor(urlEqualTo(SONGS_URL))
                .withRequestBody(matchingJsonPath("$.id", equalTo(String.valueOf(resourceId))));

        await().atMost(TIMEOUT).untilAsserted(() -> ComponentTestEnvironment.SONG_SERVICE.verify(1, request));
        // and no second call (a retry) comes later
        await().during(QUIET_PERIOD).atMost(QUIET_PERIOD.plusSeconds(5)).untilAsserted(() ->
                ComponentTestEnvironment.SONG_SERVICE.verify(1, request));
    }

    @Then("the metadata of resource {long} is saved after {int} attempts")
    public void metadataSavedAfterAttempts(long resourceId, int attempts) {
        var request = postRequestedFor(urlEqualTo(SONGS_URL))
                .withRequestBody(matchingJsonPath("$.id", equalTo(String.valueOf(resourceId))));

        await().atMost(TIMEOUT).untilAsserted(() ->
                ComponentTestEnvironment.SONG_SERVICE.verify(attempts, request));
        // the last attempt is the successful one and nothing is sent after it
        var statuses = ComponentTestEnvironment.SONG_SERVICE.getAllServeEvents().stream()
                .sorted((a, b) -> a.getRequest().getLoggedDate().compareTo(b.getRequest().getLoggedDate()))
                .map(event -> event.getResponse().getStatus())
                .toList();
        assertThat(statuses).containsExactly(503, 503, 200);
        await().during(QUIET_PERIOD).atMost(QUIET_PERIOD.plusSeconds(5)).untilAsserted(() ->
                ComponentTestEnvironment.SONG_SERVICE.verify(attempts, request));
    }

    @Then("resource service is asked for resource {long} exactly once")
    public void resourceServiceAskedOnce(long resourceId) {
        var request = getRequestedFor(urlEqualTo("/resources/" + resourceId));

        await().atMost(TIMEOUT).untilAsserted(() -> ComponentTestEnvironment.RESOURCE_SERVICE.verify(1, request));
        await().during(QUIET_PERIOD).atMost(QUIET_PERIOD.plusSeconds(5)).untilAsserted(() ->
                ComponentTestEnvironment.RESOURCE_SERVICE.verify(1, request));
    }

    @Then("song service is never called")
    public void songServiceNeverCalled() {
        // the processing has to be over first, otherwise "never" is checked too early
        eventIsAcknowledged();
        assertThat(ComponentTestEnvironment.SONG_SERVICE.findAll(postRequestedFor(urlEqualTo(SONGS_URL))))
                .isEmpty();
    }

    @Then("the event is acknowledged and is not delivered again")
    public void eventIsAcknowledged() {
        // ready = waiting for a consumer, unacked = taken by the listener but not confirmed yet.
        // Both zero means the broker has the confirmation and will not deliver the message again.
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(queueCounts()).containsExactly(0, 0));
        await().during(QUIET_PERIOD).atMost(QUIET_PERIOD.plusSeconds(5)).untilAsserted(() ->
                assertThat(queueCounts()).containsExactly(0, 0));
    }

    // ---------- helpers ----------

    /**
     * Asks the broker itself: {ready, unacknowledged} messages of the queue.
     */
    private int[] queueCounts() throws IOException, InterruptedException {
        var result = ComponentTestEnvironment.RABBIT.execInContainer(
                "rabbitmqctl", "list_queues", "--quiet", "--no-table-headers",
                "name", "messages_ready", "messages_unacknowledged");
        var line = result.getStdout().lines()
                .filter(l -> l.startsWith(queue))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Queue '" + queue + "' not found: " + result.getStdout()));
        var matcher = QUEUE_COUNTS.matcher(line.substring(queue.length()));
        assertThat(matcher.find()).as("counts in '%s'", line).isTrue();
        return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
    }

    private static byte[] readFile(String fileName) throws IOException {
        try (var stream = MetadataProcessingSteps.class.getResourceAsStream("/" + fileName)) {
            assertThat(stream).as("sample file '%s' on the classpath", fileName).isNotNull();
            return stream.readAllBytes();
        }
    }

}
