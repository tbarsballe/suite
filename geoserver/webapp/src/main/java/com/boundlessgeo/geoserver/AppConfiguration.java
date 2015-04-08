/* (c) 2015 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.geoserver.catalog.Catalog;
import org.geoserver.config.GeoServerDataDirectory;
import org.geotools.util.logging.Logging;
import org.springframework.web.context.ServletContextAware;

/**
 * Tracks suite specific configuration, such as the Thumbnail Cache directory
 */
public class AppConfiguration implements ServletContextAware {
    Catalog catalog;
    ServletContext servletContext;
    
    private String cacheDir;
    
    private static Logger LOGGER = Logging.getLogger(AppConfiguration.class);
    
    public AppConfiguration(Catalog catalog) {
        this.catalog = catalog;
    }
    
    /**
     * Determines the location of the composer thumbnail cache directory based on the following lookup
     * mechanism:
     *  
     * 1) Java environment variable
     * 2) Servlet context variable
     * 3) System variable 
     *
     * For each of these, the methods checks that
     * 1) The path exists
     * 2) Is a directory
     * 3) Is writable
     * 
     * If all of these lookups fail, uses the default value of GEOSERVER_DATA_DIR/composer
     * 
     * @param servContext The servlet context.
     * @return String The absolute path to the data directory, or <code>null</code> if it could not
     * be found. 
     */
    private String lookupCacheDirectory(ServletContext servContext) {
        
        final String[] typeStrs = { "Java environment variable ",
                "Servlet context parameter ", "System environment variable " };
        
        final String[] varStrs = { "COMPOSER_CACHE_DIR" };
        
        String cacheDir = null;
        
        // Loop over variable names
        for (int i = 0; i < varStrs.length && cacheDir == null; i++) {
            // Loop over variable access methods
            for (int j = 0; j < typeStrs.length && cacheDir == null; j++) {
                String value = null;
                String varStr = varStrs[i];
                String typeStr = typeStrs[j];
                
                // Lookup section
                switch (j) {
                case 0:
                    value = System.getProperty(varStr);
                    break;
                case 1:
                    value = servContext.getInitParameter(varStr);
                    break;
                case 2:
                    value = System.getenv(varStr);
                    break;
                }
                
                if (value == null || value.equalsIgnoreCase("")) {
                    LOGGER.finer("Found " + typeStr + varStr + " to be unset");
                    continue;
                }
                
                // Verify section
                if (verifyPath(value, "Found " + typeStr + varStr + " set to " + value)) {
                    cacheDir = value;
                }
            }
        }
        
        //Use the default of data/composer
        if(cacheDir == null) {
            cacheDir = (new GeoServerDataDirectory(catalog.getResourceLoader()).root().getPath())
                    +File.separator+"composer";
            if (verifyPath(cacheDir, "Trying default composer cache directory " + cacheDir)) {
                LOGGER.info("COMPOSER_CACHE_DIR not set, using default value: " + cacheDir);
            }
        }
        try {
            File cacheFile = new File(cacheDir);
            if (!cacheFile.exists()) {
                cacheFile.mkdirs();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not initilize composer cache directory", e);
        }
        return cacheDir;
    }
    
    private boolean verifyPath(String path, String msgPrefix) {
        File fh = new File(path);
        
        if (!fh.exists()) {
            LOGGER.warning(msgPrefix + " , but this path does not exist");
            return false;
        }
        if (!fh.isDirectory()) {
            LOGGER.warning(msgPrefix + " , which is not a directory");
            return false;
        }
        if (!fh.canWrite()) {
            LOGGER.warning(msgPrefix + " , which is not writeable");
            return false;
        }
        return true;
    }

    @Override
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
        cacheDir = lookupCacheDirectory(servletContext);
    }

    public String getCacheDir() {
        return cacheDir;
    }
    
    /**
     * Retrieves a file from the cache directory denoted by a relative path. 
     * If the file does not exist, creates a new file.
     * @param path relative path within cache dir
     * @return the file
     * @throws IOException if there is an error creating the file
     */
    public File createCacheFile(String path) throws IOException {
        File file = new File(cacheDir + File.separator + path);
        if (!file.exists()) {
            file.createNewFile();
        }
        return file;
    }
    /**
     * Retrieves a file from the cache directory denoted by a relative path. 
     * If the file does not exist, throws a FileNotFoundException
     * @param path relative path within cache dir
     * @return the file
     * @throws FileNotFoundException if the file does not exist
     */
    public File getCacheFile(String path) throws FileNotFoundException {
        File file = new File(cacheDir + File.separator + path);
        if (!file.exists()) {
            throw new FileNotFoundException(cacheDir + File.separator + path);
        }
        return file;
    }
}
