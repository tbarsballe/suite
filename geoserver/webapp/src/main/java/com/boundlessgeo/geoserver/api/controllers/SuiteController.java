package com.boundlessgeo.geoserver.api.controllers;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;

import javax.servlet.http.HttpServletRequest;

import org.geoserver.config.GeoServer;
import org.geoserver.ows.util.ResponseUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.boundlessgeo.geoserver.json.JSONObj;

@Controller
@RequestMapping("/api/suite")
public class SuiteController extends ApiController {
    
    @Autowired
    public SuiteController(GeoServer geoServer) {
        super(geoServer);
    }
    
    @RequestMapping(value = "/version", method = RequestMethod.GET)
    public @ResponseBody JSONObj version() {
        String version = getClass().getPackage().getImplementationVersion();
        return new JSONObj().put("version", version);
    }
    
    @RequestMapping(value = "/docs", method = RequestMethod.GET)
    public @ResponseBody JSONObj docLink(HttpServletRequest request) throws IOException {
        JSONObj obj = new JSONObj();
        
        //Local URL
        URL docURL = new URL(ResponseUtils.baseURL(request) + "opengeo-docs/index.html");
        HttpURLConnection connection = (HttpURLConnection) docURL.openConnection();
        connection.setRequestMethod("GET");
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            obj.put("url", docURL.getPath());
            obj.put("type", "local");
            return obj;
        }
        
        //Remote URL (Current version)
        String version = getClass().getPackage().getImplementationVersion();
        
        docURL = new URL("http://suite.opengeo.org/docs/" + version + "/index.html");
        connection = (HttpURLConnection) docURL.openConnection();
        connection.setRequestMethod("GET");
        responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            obj.put("url", docURL.getPath());
            obj.put("type", "remote");
            return obj;
        }
        
        //Remote URL (Latest)
        obj.put("url", "http://suite.opengeo.org/docs/latest/index.html");
        obj.put("type", "remote");
        return obj;
    }
}
