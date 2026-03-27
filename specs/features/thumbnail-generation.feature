@existing-behavior
Feature: Async Thumbnail Generation
  As a Content Manager
  I want thumbnails generated automatically after upload
  So that the gallery displays optimized previews

  Scenario: Generate thumbnail for JPEG image
    Given a JPEG image processing message is received
    When the worker processes the message
    Then a thumbnail should be generated with max dimension 600px
    And the thumbnail should be uploaded to storage with "_thumbnail" suffix
    And the image metadata should be updated with thumbnail references

  Scenario: Generate thumbnail for PNG image
    Given a PNG image processing message is received
    When the worker processes the message
    Then a thumbnail should be generated preserving transparency

  Scenario: Skip message for different storage type
    Given a message with storage type "local" is received
    And the worker is configured for "cloud" storage
    When the worker processes the message
    Then the message should be acknowledged without processing

  Scenario: Handle processing failure
    Given an invalid image processing message is received
    When the worker attempts to process the message
    Then the message should be rejected to the dead letter exchange
    And temporary files should be cleaned up
