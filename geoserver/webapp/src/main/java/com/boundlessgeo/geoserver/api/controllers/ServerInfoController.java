/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import com.boundlessgeo.geoserver.json.JSONArr;
import com.boundlessgeo.geoserver.json.JSONObj;
import com.boundlessgeo.geoserver.util.RecentObjectCache;
import com.boundlessgeo.geoserver.util.RecentObjectCache.Ref;

import java.util.Map;
import java.util.Set;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.config.ServiceInfo;
import org.geoserver.config.SettingsInfo;
import org.geotools.data.Parameter;
import org.geotools.process.ProcessFactory;
import org.geotools.process.Processors;
import org.geotools.util.Version;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Summarizes information about the GeoServer instance.
 */
@Controller
@RequestMapping("/api/serverInfo")
public class ServerInfoController extends ApiController {

    @Autowired
    public ServerInfoController(GeoServer geoServer, RecentObjectCache recent) {
        super(geoServer, recent);
    }

    @RequestMapping(method= RequestMethod.GET)
    public @ResponseBody
    JSONObj get() {
        JSONObj obj = new JSONObj();
        obj.toObject();

        SettingsInfo settings = geoServer.getGlobal().getSettings();
        obj.putObject("service")
           .put("title", settings.getTitle());

        JSONObj services = obj.putObject("services");
        for (ServiceInfo service : geoServer.getServices()) {
            JSONArr versions = services.putObject(service.getName())
               .put("title", service.getTitle())
               .putArray("versions");

            for (Version ver : service.getVersions()) {
                versions.add(ver.toString());
            }
        }

        Catalog cat = geoServer.getCatalog();
        obj.put("workspaces", cat.count(WorkspaceInfo.class, Filter.INCLUDE))
           .put("layers", cat.count(LayerInfo.class, Filter.INCLUDE))
           .put("maps", cat.count(LayerGroupInfo.class, Filter.INCLUDE));

        JSONObj cache = obj.putObject("recent");

        JSONArr recentMaps = cache.putArray("maps");
        for (Ref ref : recent.list(LayerGroupInfo.class)) {
            IO.ref(recentMaps.addObject(), ref);
        }

        return obj;
    }
    
    @RequestMapping(value = "/renderingTransforms", method = RequestMethod.GET)
    public @ResponseBody JSONArr renderingTransforms() {
        Set<ProcessFactory> processors = Processors.getProcessFactories();
        JSONArr arr = new JSONArr();
        
        for (ProcessFactory processFactory : processors) {
            for (Name name : processFactory.getNames()) {
                Map<String, Parameter<?>> processInfo = processFactory.getParameterInfo(name);
                Map<String, Parameter<?>> resultInfo = processFactory.getResultInfo(name, null);
                
                if (resultInfo.get("result") == null) {
                    continue;
                }
                Class<?> returnType = resultInfo.get("result").getType();
                
                //Only include processes that return a geometry or a raster
                if (com.vividsolutions.jts.geom.Geometry.class.isAssignableFrom(returnType) || 
                        org.geotools.data.simple.SimpleFeatureCollection.class.isAssignableFrom(returnType) ||
                        org.geotools.coverage.grid.GridCoverage2D.class.isAssignableFrom(returnType)) {
                    JSONObj process = arr.addObject();
                    process.put("name", name.toString());
                    JSONObj params = process.putObject("params");
                    for (String key : processInfo.keySet()) {
                        JSONObj param = params.putObject(key);
                        param.put("description", processInfo.get(key).getDescription() == null ? 
                                null : processInfo.get(key).getDescription().toString());
                        param.put("type", processInfo.get(key).getType().getName());
                        param.put("default", processInfo.get(key).getDefaultValue() == null ?
                                null : processInfo.get(key).getDefaultValue().toString());
                        param.put("required", processInfo.get(key).isRequired());
                        param.put("minOccurs", processInfo.get(key).getMinOccurs());
                        param.put("maxOccurs", processInfo.get(key).getMaxOccurs());
                    }
                }
            }
        }
        
        return arr;
    }
}
