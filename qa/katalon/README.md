# Boundless Server QA - Katalon

[Katalon](https://www.katalon.com/) is used for integration testing Web UI and Web API components in Boundless Server.

*The following content is currently under construction, and subject to change at any time.*

## Running Tests

### Prerequisites:

Before running any tests using Katalon, the Boundless Server instance to be tested must be running on http://localhost:8080.

In addition, the [test data bundle](../test_data) must be copied into the GeoServer data directory of that server instance.

### Running Tests using Katalon Studio

*Katalon Studio must be installed*

Open Katalon Studio.

Select "File > Open Project", and navigate to this directory.

Under "Test Suites", select the Test Suite you wish to run. Click the "Run" button.


### Running Tests using Command Line

*Katalon Studio must be installed*

Windows:

    {path_to_katalon}\katalon -runMode=console -consoleLog -noExit -projectPath="%cd%" -testSuitePath="Test Suites/{test_suite_name}" -browserType="Chrome"

Mac OS:

    /Applications/Katalon\ Studio.app/Contents/MacOS/katalon --args -runMode=console -consoleLog -projectPath=$(pwd) -testSuitePath=Test\ Suites/{test_suite_name} -browserType=Chrome

Linux:

    {path_to_katalon}/katalon -runMode=console -consoleLog -projectPath=$(pwd) -testSuitePath=Test\ Suites/{test_suite_name} -browserType=Chrome

Where `{path_to_katalon}` and `{test_suite_name}` are replaced with the appropriate values.

Execution reports will be generated in the Reports directory.

### Running Tests using Docker

*Docker must be installed*
*Katalon Studio does **not** need to be installed*

    docker run --rm -v $PWD:/katalon/katalon/source:ro -e BROWSER_TYPE="Chrome" -e TEST_SUITE_PATH="Test Suites/{test_suite_name}" -e DOCKER_PROXY_ALIAS="nginx" quay.io/boundlessgeo/b7s-katalon:federal

Where `{test_suite_name}` is replaced with the appropriate value.

## Adding New Tests

### Style Guide

Test Cases

- Subfolder - <Component>
- Naming scheme - <Type>_[<Subcomponent>_]_<ShortDescription>


### Adding a new web test

Ensure "Create new folder(s) as page's name" is checked, unless the requesite folders already exist

### Adding a new api test