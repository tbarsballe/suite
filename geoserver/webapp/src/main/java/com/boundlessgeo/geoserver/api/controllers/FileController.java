package com.boundlessgeo.geoserver.api.controllers;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerDataDirectory;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Resources;
import org.geotools.util.logging.Logging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.boundlessgeo.geoserver.json.JSONArr;
import com.boundlessgeo.geoserver.json.JSONObj;

/**
 * Information about the filesystem contents.
 * <p>
 * This API is locked down for map composer and is (not intended to be stable between releases).</p>
 * 
 * @see <a href="https://github.com/boundlessgeo/suite/wiki/Files-API">File API</a> (Wiki)
 */
 @Controller
 @RequestMapping("/api/files")
public class FileController extends ApiController {
    static Logger LOG = Logging.getLogger(FileController.class);
    
    protected static final int DEFAULT_FILE_DEPTH = 3;
    
    @Autowired
    public FileController(GeoServer geoServer) {
        super(geoServer);
    }
    
    //get in root
    @RequestMapping(method = RequestMethod.GET)
    public @ResponseBody JSONObj get(
            @RequestParam(value="file", required=true) String pathname,
            @RequestParam(value="depth", required=false, defaultValue=""+DEFAULT_FILE_DEPTH) Integer depth,
            @RequestParam(value="filter", required=false) String filter, 
            HttpServletRequest req) {
        
        JSONObj obj = new JSONObj();
        File file = new File(pathname);
        
        try {
            if (filter == null) {
                return IO.files(obj, file, depth);
            }
            return IO.files(obj, file, depth, new StringFilenameFilter (filter));
            
        } catch (IOException e ) {
            throw new RuntimeException("Error reading filesystem", e);
        }
    }
    
  //get in workspace?
    @RequestMapping(value = "/roots", method = RequestMethod.GET)
    public @ResponseBody JSONArr roots() {
        Catalog cat = geoServer.getCatalog();
        GeoServerResourceLoader loader = cat.getResourceLoader();
        
        JSONArr roots = new JSONArr();
        JSONObj root;
        
        //System roots
        for (File file : File.listRoots()) {
            root = roots.addObject();
            root(root, file);
        }
        
        //Data Dir:
        File dataDirectory = loader.getBaseDirectory();
        root = roots.addObject();
        root(root, dataDirectory, "Data Directory");
        
        //Home Dir:
        File homeDirectory = null;
        String home = System.getProperty("user.home");
        if(home != null) {
            homeDirectory = new File(home);
            root = roots.addObject();
            root(root, homeDirectory, "Home Directory");
        }
        
        return roots;
    }
    
    private static JSONObj root(JSONObj obj, File file) {
        return root (obj, file, file.getName());
    }
    
    private static JSONObj root(JSONObj obj, File file, String name) {
        obj.put("name", name);
        obj.put("path", file.getPath());
        return obj;
    }
    
  //get in workspace?
    @RequestMapping(value = "/recent", method = RequestMethod.GET)
    public @ResponseBody
    JSONObj recent(@PathVariable String wsName) {
        Catalog cat = geoServer.getCatalog();
        JSONObj obj = new JSONObj();
        
        
        return obj;
    }
    
    //get in workspace?
    @RequestMapping(value = "/{wsName}", method = RequestMethod.GET)
    public @ResponseBody
    JSONObj getWorkspace(@PathVariable String wsName) {
        Catalog cat = geoServer.getCatalog();
        WorkspaceInfo ws = findWorkspace(wsName, cat);
        
        GeoServerDataDirectory dd = new GeoServerDataDirectory(cat.getResourceLoader());
        File wsDir = Resources.directory(dd.get(ws));
        JSONObj obj = new JSONObj();
        
        return root(obj, wsDir); 
    }
    
 
    
    
    private class WorkspaceFileFilter implements FilenameFilter {
        File wsRoot;
    
        WorkspaceFileFilter(WorkspaceInfo ws, Catalog cat) {
            GeoServerDataDirectory dd = new GeoServerDataDirectory(cat.getResourceLoader());
            wsRoot = Resources.directory(dd.get(ws));
        }
        @Override
        public boolean accept(File dir, String name) {
            return dir.getPath().contains(wsRoot.getPath());
        }
    }
    
    private class StringFilenameFilter implements FilenameFilter {
        String[] extensions;
    
        StringFilenameFilter(String filter) {
            if ("".equals(filter.trim())) {
                extensions = new String[0];
            } else {
                extensions = filter.split(",");
                for (int i = 0; i < extensions.length; i++) {
                    extensions[i] = extensions[i].trim();
                }
            }
        }
        
        @Override
        public boolean accept(File dir, String name) {
            if (dir == null || name == null) {
                return false;
            }
            File file = new File(dir.getPath()+File.separator+name);
            if (file.isDirectory()) {
                return true;
            }
            boolean match = false;
            for (String extension : extensions) {
                if (name.endsWith("."+extension)) {
                    match = true;
                }
            }
            return match;
        }
    }
}
