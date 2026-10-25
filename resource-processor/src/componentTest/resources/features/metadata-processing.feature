Feature: Metadata extraction for uploaded resources
When a resource is uploaded, resource-processor receives an event, downloads the MP3 file from resource service,
reads its tags and saves the metadata to song service.

  Scenario: Metadata of an uploaded MP3 is saved to the song catalog
    Given resource service stores the file "valid-sample-with-required-tags.mp3" as resource 1
    And song service accepts new metadata
    When the event "resource uploaded" with resource id 1 arrives
    Then song service receives the metadata of resource 1
      | name     | Test Title  |
      | artist   | Test Artist |
      | album    | Test Album  |
      | duration | 00:08       |
      | year     | 2025        |

  Scenario: MP3 without required tags is rejected by the song catalog
    Given resource service stores the file "invalid-sample-with-missed-tags.mp3" as resource 2
    And song service rejects the metadata with status 400
    When the event "resource uploaded" with resource id 2 arrives
    Then song service is called exactly once for resource 2
    And the event is acknowledged and is not delivered again

  Scenario: Song service is temporarily unavailable
    Given resource service stores the file "valid-sample-with-required-tags.mp3" as resource 1
    And song service answers with 503 twice and then accepts the metadata
    When the event "resource uploaded" with resource id 1 arrives
    Then the metadata of resource 1 is saved after 3 attempts

  Scenario: The same event is delivered twice
    Given resource service stores the file "valid-sample-with-required-tags.mp3" as resource 1
    And song service answers 409 because the metadata already exists
    When the event "resource uploaded" with resource id 1 arrives
    Then song service is called exactly once for resource 1
    And the event is acknowledged and is not delivered again

  Scenario: Resource service does not know the resource
    Given resource service has no resource 3
    And song service accepts new metadata
    When the event "resource uploaded" with resource id 3 arrives
    Then resource service is asked for resource 3 exactly once
    And song service is never called
    And the event is acknowledged and is not delivered again
