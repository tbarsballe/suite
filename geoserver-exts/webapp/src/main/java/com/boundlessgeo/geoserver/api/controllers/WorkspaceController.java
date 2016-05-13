/* (c) 2014 - 2015 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import com.boundlessgeo.geoserver.api.exceptions.BadRequestException;
import com.boundlessgeo.geoserver.json.JSONArr;
import com.boundlessgeo.geoserver.json.JSONObj;
import com.boundlessgeo.geoserver.util.RecentObjectCache;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.io.FileUtils;
import org.geoserver.catalog.CascadeDeleteVisitor;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.config.GeoServer;
import org.geoserver.data.util.IOUtils;

import com.boundlessgeo.geoserver.bundle.BundleExporter;
import com.boundlessgeo.geoserver.bundle.BundleImporter;
import com.boundlessgeo.geoserver.bundle.ExportOpts;
import com.boundlessgeo.geoserver.bundle.ImportOpts;

import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resource.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

@Controller
@RequestMapping("/api/workspaces")
public class WorkspaceController extends ApiController {

    public static final String APPLICATION_ZIP_VALUE = "application/zip";

    @Autowired
    public WorkspaceController(GeoServer geoServer, RecentObjectCache recentCache) {
        super(geoServer, recentCache);
    }

    @RequestMapping(method = RequestMethod.GET)
    public @ResponseBody
    JSONArr list() {
        JSONArr arr = new JSONArr();

        Catalog cat = geoServer.getCatalog();

        WorkspaceInfo def = cat.getDefaultWorkspace();
        if (def != null) {
           workspace(arr.addObject(), def, namespaceFor(def), true);
        }

        try (
            CloseableIterator<WorkspaceInfo> list = cat.list(WorkspaceInfo.class, Predicates.acceptAll());
        ) {
            while(list.hasNext()) {
                WorkspaceInfo ws = list.next();
                if (def != null && ws.getName().equals(def.getName())) {
                    continue;
                }

                NamespaceInfo ns = namespaceFor(ws);
                workspace(arr.addObject(), ws, ns, false);
            }
        }

        return arr;
    }

    @RequestMapping(value = "/recent", method = RequestMethod.GET)
    public @ResponseBody JSONArr recent() {
        Catalog cat = catalog();

        JSONArr arr = new JSONArr();

        for (RecentObjectCache.Ref ref : recent.list(WorkspaceInfo.class)) {
            WorkspaceInfo ws = cat.getWorkspaceByName(ref.name);
            if (ws != null) {
                NamespaceInfo ns = cat.getNamespaceByPrefix(ref.name);
                workspace(arr.addObject(), ws, ns, false);
            }
        }
        return arr;
    }

    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public @ResponseBody
    JSONObj create(@RequestBody JSONObj obj) {
        Catalog cat = geoServer.getCatalog();

        String wsName = obj.str("name");
        if (wsName == null) {
            throw new BadRequestException("Workspace must have a name");
        }

        String nsUri = obj.str("uri");
        if (nsUri == null) {
            nsUri = "http://" + wsName;
        }

        boolean isDefault = obj.has("default") ? obj.bool("default") : false;

        WorkspaceInfo ws = cat.getFactory().createWorkspace();
        ws.setName(wsName);

        NamespaceInfo ns = cat.getFactory().createNamespace();
        ns.setPrefix(wsName);
        ns.setURI(nsUri);

        Metadata.created(ws, new Date());

        cat.add(ws);
        cat.add(ns);
        recent.add(WorkspaceInfo.class, ws);

        if (isDefault) {
            cat.setDefaultWorkspace(ws);
            cat.setDefaultNamespace(ns);
        }

        return workspace(new JSONObj(), ws, ns, isDefault);
    }

    @RequestMapping(value = "/{wsName:.+}", method = RequestMethod.GET)
    public @ResponseBody JSONObj get(@PathVariable String wsName) {
        Catalog cat = geoServer.getCatalog();

        WorkspaceInfo ws = findWorkspace(wsName, cat);
        WorkspaceInfo def = cat.getDefaultWorkspace();

        JSONObj obj = IO.workspace(new JSONObj(), ws, namespaceFor(ws), def != null && def.equals(ws));

        obj.put("maps", countMaps(ws, cat));
        obj.put("layers", countLayers(ws, cat));
        obj.put("stores", countStores(ws, cat));

        return obj;
    }

    JSONObj workspace(JSONObj obj, WorkspaceInfo ws, NamespaceInfo ns, boolean isDefault) {
        IO.workspace(obj, ws, ns, isDefault);
        if (!obj.has(Metadata.MODIFIED)) {
            // derive from resource
            Resource r = dataDir().config(ws);
            if (r.getType() != Type.UNDEFINED) {
                IO.date(obj.putObject("modified"), new Date(r.lastmodified()));
            }
        }
        return obj;
    }

    Integer countMaps(WorkspaceInfo ws, Catalog cat) {
        return cat.count(LayerGroupInfo.class, Predicates.equal("workspace.name", ws.getName()));
    }

    Integer countLayers(WorkspaceInfo ws, Catalog cat) {
        return cat.count(LayerInfo.class, Predicates.equal("resource.namespace.prefix", ws.getName()));
    }

    Integer countStores(WorkspaceInfo ws, Catalog cat) {
        return cat.count(StoreInfo.class, Predicates.equal("workspace.name", ws.getName()));
    }

    @RequestMapping(value = "/{wsName:.+}", method = RequestMethod.PATCH)
    public @ResponseBody JSONObj patch(@PathVariable String wsName, @RequestBody JSONObj obj) {
        return put(wsName, obj);
    }

    @RequestMapping(value = "/{wsName:.+}", method = RequestMethod.PUT)
    public @ResponseBody JSONObj put(@PathVariable String wsName, @RequestBody JSONObj obj) {
        Catalog cat = geoServer.getCatalog();

        WorkspaceInfo ws = findWorkspace(wsName, cat);
        NamespaceInfo ns = namespaceFor(ws);

        String name = obj.str("name");
        if (name != null) {
            ws.setName(name);
            ns.setPrefix(name);
        }

        String uri = obj.str("uri");
        if (uri != null) {
            ns.setURI(uri);
        }
        Metadata.modified(ws, new Date());
        cat.save(ws);
        cat.save(ns);
        recent.add(WorkspaceInfo.class, ws);

        Boolean isDefault = obj.bool("default");
        if (Boolean.TRUE.equals(isDefault)) {
            cat.setDefaultWorkspace(ws);
            cat.setDefaultNamespace(ns);
        }
        else if (Boolean.FALSE.equals(isDefault)) {
            WorkspaceInfo defaultWorkspace = cat.getDefaultWorkspace();
            if( defaultWorkspace != null && defaultWorkspace.getId().equals( ws.getId())){
                cat.setDefaultWorkspace(null);
                cat.setDefaultNamespace(null);
            }
        }
        return IO.workspace(new JSONObj(), ws, ns, isDefault == Boolean.TRUE);
    }

    @RequestMapping(value = "/{wsName:.+}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String wsName, HttpServletResponse response) {
        Catalog cat = geoServer.getCatalog();
        
        WorkspaceInfo ws = findWorkspace(wsName, cat);
        new CascadeDeleteVisitor(cat).visit(ws);
        
        recent.remove(WorkspaceInfo.class, ws);
    }

    @RequestMapping(value = "/{wsName}/export", method = RequestMethod.POST, produces = APPLICATION_ZIP_VALUE)
    @ResponseStatus(value = HttpStatus.OK)
    public void export(@PathVariable String wsName, HttpServletResponse response) throws Exception {
        Catalog cat = geoServer.getCatalog();

        WorkspaceInfo ws = findWorkspace(wsName, cat);
        BundleExporter exporter = new BundleExporter(cat, new ExportOpts(ws));
        exporter.run();
        
        Path zip = exporter.zip();
        
        response.setContentType(APPLICATION_ZIP_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename=\""+zip.getFileName()+"\"");
        FileUtils.copyFile(zip.toFile(), response.getOutputStream());
    }


    @RequestMapping(value = "/{wsName}/import", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(value = HttpStatus.OK)
    public void inport(@PathVariable String wsName, HttpServletRequest request, HttpServletResponse response)
        throws Exception {
        Catalog cat = geoServer.getCatalog();
        
        WorkspaceInfo ws = findWorkspace(wsName, cat);
        
        // grab the uploaded file
        FileItemIterator files = doFileUpload(request);
        if (!files.hasNext()) {
            throw new BadRequestException("Request must contain a single file");
        }
        Path zip = Files.createTempFile(null, null);
        FileItemStream item = files.next();
        try (InputStream stream = item.openStream()) {
            IOUtils.copy(stream, zip.toFile());
        }
        BundleImporter importer = new BundleImporter(cat, new ImportOpts(ws));
        importer.unzip(zip);
        importer.run();
    }

}
