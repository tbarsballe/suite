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
import com.kms.katalon.core.testobject.TestObject as TestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WSBuiltInKeywords
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUiBuiltInKeywords
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import internal.GlobalVariable as GlobalVariable
import org.openqa.selenium.Keys as Keys

WebUI.openBrowser('')

WebUI.navigateToUrl('http://localhost:8080/geoserver/web/')

WebUI.setText(findTestObject('Web/GeoServer/Page_GeoServer Welcome/input_username'), 'admin')

WebUI.setText(findTestObject('Web/GeoServer/Page_GeoServer Welcome/input_password'), 'geoserver')

WebUI.sendKeys(findTestObject('Web/GeoServer/Page_GeoServer Welcome/input_password'), Keys.chord(Keys.ENTER))

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer Welcome/a_Create workspaces'))

WebUI.setText(findTestObject('Web/GeoServer/Page_GeoServer New Workspace/input_pname'), 'test')

WebUI.setText(findTestObject('Web/GeoServer/Page_GeoServer New Workspace/input_uri'), 'http://test.com')

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer New Workspace/a_Submit'))

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer Workspaces/span_Import Data'))

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer Import Data/span_Next'))

WebUI.setText(findTestObject('Web/GeoServer/Page_GeoServer Import Data/input_panelcontentformfile'), '/Users/tbarsballe/Documents/data/spatial_files/shapefiles/medford/workspaces/medford/data/buildings.zip')

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer Import Data/a_Next'))

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer Import Tasks/input_taskslistContaineritems1'))

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer Import Tasks/span_Import'))

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer Import Tasks/span_Import successful.'))

WebUI.verifyElementPresent(findTestObject('Web/GeoServer/Page_GeoServer Import Tasks/span_Import successful.'), 10)

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer Import Tasks/span_Workspaces'))

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer Workspaces/input_tablelistContaineritems1'))

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer Workspaces/a_Remove selected workspace(s)'))

WebUI.click(findTestObject('Web/GeoServer/Page_GeoServer Workspaces/a_OK'))

WebUI.closeBrowser()

