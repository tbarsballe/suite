import static com.kms.katalon.core.checkpoint.CheckpointFactory.findCheckpoint
import static com.kms.katalon.core.testcase.TestCaseFactory.findTestCase
import static com.kms.katalon.core.testdata.TestDataFactory.findTestData
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

import com.kms.katalon.core.checkpoint.Checkpoint as Checkpoint
import com.kms.katalon.core.checkpoint.CheckpointFactory as CheckpointFactory
import com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords as MobileBuiltInKeywords
import com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords as Mobile
import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.testcase.TestCase as TestCase
import com.kms.katalon.core.testcase.TestCaseFactory as TestCaseFactory
import com.kms.katalon.core.testdata.TestData as TestData
import com.kms.katalon.core.testdata.TestDataFactory as TestDataFactory
import com.kms.katalon.core.testobject.ObjectRepository as ObjectRepository
import com.kms.katalon.core.testobject.RequestObject as RequestObject
import com.kms.katalon.core.testobject.ResponseObject as ResponseObject
import com.kms.katalon.core.testobject.TestObject as TestObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WSBuiltInKeywords
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUiBuiltInKeywords
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import internal.GlobalVariable as GlobalVariable
import groovy.json.JsonSlurper as JsonSlurper
import java.nio.file.Files as Files
import java.nio.file.Paths as Paths

import org.apache.commons.io.IOUtils

'create workspace test'
WS.sendRequest(findTestObject('Api/GeoServer/rest-workspace-test-POST'))

'create import'
response = WS.sendRequest(findTestObject('Api/GeoServer/rest-import-create-POST'))

WS.verifyResponseStatusCode(response, 201)

'create import task'
println response.getResponseBodyContent();
def results = new groovy.json.JsonSlurper().parseText( response.getResponseBodyContent() )
def id = results.get("import").get("id");
RequestObject request = findTestObject('Api/GeoServer/rest-import-task-create-POST')
request.setRestUrl("http://localhost:8080/geoserver/rest/imports/"+id+"/tasks")

'set content type to form-data'
String contentType = "multipart/form-data; boundary=------------------------e6654f8500a58693"
((TestObjectProperty)request.getHttpHeaderProperties().get(1)).setValue(contentType)

'read file'
File file = new File("/Users/tbarsballe/Documents/data/spatial_files/shapefiles/medford/workspaces/medford/data/buildings.zip")
byte[] bytes = IOUtils.toByteArray(new FileInputStream(file))

'TODO: convert file to form body'


'send file as new task'
response = WS.sendRequest(request)

'verify task created'
WS.verifyResponseStatusCodeInRange(response, 200, 204)
WS.verifyElementPropertyValue(response, "task.data.format", "Shapefile")
WS.verifyElementPropertyValue(response, "task.data.file", "buildings.shp")
WS.verifyElementPropertyValue(response, "task.dataStore.name", "buildings")
WS.verifyElementPropertyValue(response, "task.layer.name", "buildings")

'run the import'
request = (RequestObject)findTestObject('Api/GeoServer/rest-import-run-POST')
request.setRestUrl("http://localhost:8080/geoserver/rest/imports/"+id)
response = WS.sendRequest(request)

WS.verifyResponseStatusCodeInRange(response, 200, 204)

'wait for import to be processed'
WS.delay(10)

