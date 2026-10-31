Feature: Music library API
  The whole stack is running: API gateway, Eureka, resource service, song service, resource processor,
  PostgreSQL, S3 storage and RabbitMQ. The tests use only the public API of the gateway.

  Scenario: Uploaded MP3 becomes available with its metadata
    When I upload the file "valid-sample-with-required-tags.mp3"
    Then I get a resource id
    And the song metadata for this id appears within 30 seconds
      | name   | Test Title  |
      | artist | Test Artist |
      | album  | Test Album  |
    And the downloaded file equals the uploaded one

  Scenario: Deleting a resource removes its file and its metadata
    Given an uploaded MP3 with processed metadata
    When I delete the resource
    Then the resource is not found
    And the song metadata is not found

  Scenario: MP3 without required tags is stored but has no metadata
    When I upload the file "invalid-sample-with-missed-tags.mp3"
    Then I get a resource id
    And the downloaded file equals the uploaded one
    And the song metadata for this id is not created

  Scenario: Metadata can not be created twice for the same id
    Given an uploaded MP3 with processed metadata
    When I create metadata for the same id manually
    Then the response status is 409

  Scenario: A resource that does not exist is a 404
    When I request a resource that does not exist
    Then the response status is 404
    And the response describes the error

  Scenario: A client that accepts only audio gets the error description too
    When I request a resource that does not exist as a client that accepts only "audio/mpeg"
    Then the response status is 404
    And the response describes the error

  Scenario: Invalid requests are rejected
    When I delete resources with the id list "abc"
    Then the response status is 400
    When I request a song with the id "abc"
    Then the response status is 400
