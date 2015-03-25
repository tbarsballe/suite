package com.boundlessgeo.geoserver.api.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geoserver.config.GeoServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.boundlessgeo.geoserver.json.JSONArr;
import com.boundlessgeo.geoserver.json.JSONObj;
import com.boundlessgeo.geoserver.util.RecentObjectCache;

/**
 * Summarizes information about the available API endpoints.
 */
@Controller
@RequestMapping("/api")
public class IndexController extends ApiController {
    
    private final RequestMappingHandlerMapping handlerMapping;
    
    @Autowired
    public IndexController(GeoServer geoServer, RecentObjectCache recent, RequestMappingHandlerMapping handlerMapping) {
        super(geoServer, recent);
        this.handlerMapping = handlerMapping;
    }
    
    @RequestMapping(method= RequestMethod.GET)
    public ModelAndView get() {
        List<String> l = new ArrayList<String>();
        for (RequestMappingInfo mapping : this.handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : mapping.getPatternsCondition().getPatterns()) {
                l.add(pattern);
            }
        }
        ModelAndView modelAndView = new ModelAndView("api");
        modelAndView.addObject("patterns", l);
        return modelAndView;
    }

}
