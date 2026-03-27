@existing-behavior
Feature: Image Detail View
  As a Content Manager
  I want to view an image at full resolution with metadata
  So that I can inspect image quality and details

  Scenario: View image details
    Given an image "test.jpg" exists in storage
    When I navigate to the detail page for "test.jpg"
    Then I should see the full-size image
    And I should see the file size
    And I should see the upload date

  Scenario: View non-existent image
    When I navigate to the detail page for a non-existent image
    Then I should be redirected to the gallery
    And I should see an error message "Image not found"
