package com.music.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * Steps of music-library.feature. Only HTTP calls to the API gateway: the tests know nothing about the services
 * behind it, the queue, the database or the storage.
 * <p>
 * Cucumber creates a new instance of this class for every scenario, so the fields are the state of one scenario.
 * Everything a scenario uploads is deleted in {@link #cleanUp()}, the scenarios do not depend on each other.
 */
public class MusicLibrarySteps {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(30);
    /**
     * How long "the metadata is not created" is watched: the processor needs a moment to react to the event.
     */
    private static final Duration QUIET_PERIOD = Duration.ofSeconds(10);
    private static final long UNKNOWN_ID = 999_999_999L;

    private final List<Long> createdResources = new ArrayList<>();

    private byte[] uploadedFile;
    private Long resourceId;
    private Response lastResponse;

    @Before
    public void prepare() {
        RestAssured.baseURI = E2eConfig.baseUrl();
        StackReadiness.awaitStack();
    }

    @After
    public void cleanUp() {
        if (createdResources.isEmpty()) {
            return;
        }
        var ids = String.join(",", createdResources.stream().map(String::valueOf).toList());
        RestAssured.given().queryParam("id", ids).delete("/resources");
    }

    // ---------- Given / When ----------

    @Given("an uploaded MP3 with processed metadata")
    public void anUploadedMp3WithProcessedMetadata() throws IOException {
        iUploadTheFile("valid-sample-with-required-tags.mp3");
        iGetAResourceId();
        theSongMetadataAppears(null);
    }

    @When("I upload the file {string}")
    public void iUploadTheFile(String fileName) throws IOException {
        uploadedFile = readSample(fileName);
        lastResponse = RestAssured.given()
                .contentType("audio/mpeg")
                .body(uploadedFile)
                .post("/resources");
        if (lastResponse.statusCode() == 200) {
            resourceId = lastResponse.jsonPath().getLong("id");
            createdResources.add(resourceId);
        }
    }

    @When("I delete the resource")
    public void iDeleteTheResource() {
        lastResponse = RestAssured.given().queryParam("id", resourceId).delete("/resources");
        assertThat(lastResponse.statusCode()).as("delete answer: %s", lastResponse.asString()).isEqualTo(200);
        assertThat(lastResponse.jsonPath().getList("ids", Long.class)).containsExactly(resourceId);
        createdResources.remove(resourceId);
    }

    @When("I create metadata for the same id manually")
    public void iCreateMetadataForTheSameIdManually() {
        var metadata = Map.of(
                "id", resourceId,
                "name", "Manual Title",
                "artist", "Manual Artist",
                "album", "Manual Album",
                "duration", "03:15",
                "year", "2024");
        lastResponse = RestAssured.given().contentType("application/json").body(metadata).post("/songs");
    }

    @When("I request a resource that does not exist")
    public void iRequestAResourceThatDoesNotExist() {
        lastResponse = RestAssured.get("/resources/" + UNKNOWN_ID);
    }

    @When("I request a resource that does not exist as a client that accepts only {string}")
    public void iRequestAResourceThatDoesNotExistAsAClientThatAcceptsOnly(String mediaType) {
        // the resource-processor downloads files with exactly this header
        lastResponse = RestAssured.given().accept(mediaType).get("/resources/" + UNKNOWN_ID);
    }

    @When("I delete resources with the id list {string}")
    public void iDeleteResourcesWithTheIdList(String ids) {
        lastResponse = RestAssured.given().queryParam("id", ids).delete("/resources");
    }

    @When("I request a song with the id {string}")
    public void iRequestASongWithTheId(String id) {
        lastResponse = RestAssured.get("/songs/" + id);
    }

    // ---------- Then ----------

    @Then("I get a resource id")
    public void iGetAResourceId() {
        assertThat(lastResponse.statusCode()).as("upload answer: %s", lastResponse.asString()).isEqualTo(200);
        assertThat(resourceId).isNotNull().isPositive();
    }

    @Then("the song metadata for this id appears within {int} seconds")
    public void theSongMetadataAppearsWithin(int seconds, Map<String, String> expectedFields) {
        assertThat(seconds).as("the step text and PROCESSING_TIMEOUT must agree")
                .isEqualTo(PROCESSING_TIMEOUT.toSeconds());
        theSongMetadataAppears(expectedFields);
    }

    @Then("the downloaded file equals the uploaded one")
    public void theDownloadedFileEqualsTheUploadedOne() {
        var response = RestAssured.given().accept("audio/mpeg").get("/resources/" + resourceId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asByteArray()).isEqualTo(uploadedFile);
    }

    @Then("the resource is not found")
    public void theResourceIsNotFound() {
        assertThat(RestAssured.get("/resources/" + resourceId).statusCode()).isEqualTo(404);
    }

    @Then("the song metadata is not found")
    public void theSongMetadataIsNotFound() {
        assertThat(RestAssured.get("/songs/" + resourceId).statusCode()).isEqualTo(404);
    }

    @Then("the song metadata for this id is not created")
    public void theSongMetadataIsNotCreated() {
        // the processor sends the incomplete metadata, song-service rejects it with 400: nothing is saved
        await().during(QUIET_PERIOD).atMost(QUIET_PERIOD.plusSeconds(10)).untilAsserted(() ->
                assertThat(RestAssured.get("/songs/" + resourceId).statusCode()).isEqualTo(404));
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int status) {
        assertThat(lastResponse.statusCode()).as("answer: %s", lastResponse.asString()).isEqualTo(status);
    }

    @Then("the response describes the error")
    public void theResponseDescribesTheError() {
        assertThat(lastResponse.contentType()).startsWith("application/json");
        assertThat(lastResponse.jsonPath().getString("errorCode")).isEqualTo(String.valueOf(lastResponse.statusCode()));
        assertThat(lastResponse.jsonPath().getString("errorMessage")).isNotBlank();
    }

    // ---------- helpers ----------

    /**
     * The upload returns at once, the metadata is saved later by the resource-processor (queue, download,
     * extraction, call to song-service). So the answer is awaited, not asked once.
     */
    private void theSongMetadataAppears(Map<String, String> expectedFields) {
        await().atMost(PROCESSING_TIMEOUT).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            var response = RestAssured.get("/songs/" + resourceId);
            assertThat(response.statusCode()).as("song %s: %s", resourceId, response.asString()).isEqualTo(200);
            if (expectedFields != null) {
                expectedFields.forEach((field, value) ->
                        assertThat(response.jsonPath().getString(field)).as(field).isEqualTo(value));
            }
        });
    }

    private static byte[] readSample(String fileName) throws IOException {
        try (var stream = MusicLibrarySteps.class.getResourceAsStream("/" + fileName)) {
            assertThat(stream).as("sample file '%s' on the classpath", fileName).isNotNull();
            return stream.readAllBytes();
        }
    }

}
