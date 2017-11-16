package com.boundlessgeo.rest;

import org.geoserver.rest.RestBaseController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


//@RestController
//@RequestMapping(path = RestBaseController.ROOT_PATH + "/helloworld")
public class SampleController extends RestBaseController {

    @GetMapping
    public String get() {
        return "Hello World";
    }

}