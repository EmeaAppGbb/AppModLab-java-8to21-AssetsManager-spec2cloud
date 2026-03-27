@existing-behavior
Feature: Image Gallery
  As a Content Manager
  I want to see all uploaded images in a gallery view
  So that I can browse my image collection

  Scenario: View gallery with images
    Given images have been uploaded to the storage
    When I navigate to the gallery page
    Then I should see image cards in a grid layout
    And each card should display the image name and file size

  Scenario: View empty gallery
    Given no images have been uploaded
    When I navigate to the gallery page
    Then I should see a message "No images found"

  Scenario: Home page redirects to gallery
    When I navigate to the home page
    Then I should be redirected to the gallery page

  Scenario: Navigate to upload from gallery
    Given I am on the gallery page
    When I click the upload button
    Then I should see the upload form
