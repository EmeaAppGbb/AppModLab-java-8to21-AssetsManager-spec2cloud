@existing-behavior
Feature: Image Download
  As a Content Manager
  I want to download the original image file
  So that I can use it outside the application

  Scenario: Download an existing image
    Given an image "test.jpg" exists in storage
    When I request to download the image
    Then I should receive the image content as a binary stream
    And the response content type should be application/octet-stream

  Scenario: Download a non-existent image
    When I request to download a non-existent image
    Then I should receive a 404 response

  Scenario: Download with path traversal attempt
    When I request to download an image with key "../etc/passwd"
    Then I should receive a 400 response
