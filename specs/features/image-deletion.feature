@existing-behavior
Feature: Image Deletion
  As a Content Manager
  I want to delete unwanted images
  So that they no longer appear in my gallery

  Scenario: Delete an existing image
    Given an image "test.jpg" exists in storage
    When I delete the image "test.jpg"
    Then the image should be removed from storage
    And the image metadata should be removed from the database
    And I should be redirected to the gallery with a success message

  Scenario: Delete with path traversal attempt
    When I attempt to delete an image with key "../../secret"
    Then I should see an error about invalid characters
    And I should be redirected to the gallery
