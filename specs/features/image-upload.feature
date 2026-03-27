@existing-behavior
Feature: Image Upload
  As a Content Manager
  I want to upload image files through the web interface
  So that they are stored and available for viewing

  Scenario: Show upload form
    Given I am on the home page
    When I navigate to the upload page
    Then I should see the upload form with a file input
    And I should see a drag-and-drop zone

  Scenario: Upload a valid image
    Given I am on the upload page
    When I select a valid image file "test.jpg"
    And I submit the upload form
    Then I should be redirected to the gallery
    And I should see a success message "File uploaded successfully"

  Scenario: Upload with no file selected
    Given I am on the upload page
    When I submit the upload form without selecting a file
    Then I should see an error message "Please select a file to upload"
    And I should remain on the upload page

  Scenario: Upload triggers thumbnail processing
    Given I am on the upload page
    When I upload a valid image file
    Then a message should be sent to the image-processing queue
    And image metadata should be saved to the database
