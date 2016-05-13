/* (c) 2014 - 2015 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import java.io.IOException;
import java.util.NoSuchElementException;

import javax.servlet.http.HttpServletRequest;

import com.boundlessgeo.geoserver.api.exceptions.BadRequestException;
import com.boundlessgeo.geoserver.util.RecentObjectCache;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerDataDirectory;

import com.boundlessgeo.geoserver.api.exceptions.NotFoundException;
import org.opengis.filter.sort.SortBy;

/**
 * Base class for api controllers.
 */
public abstract class ApiController {
    public static final int DEFAULT_PAGESIZE = 25;

    protected GeoServer geoServer;

    protected RecentObjectCache recent;

    public ApiController(GeoServer geoServer) {
        this(geoServer, null);
    }

    public ApiController(GeoServer geoServer, RecentObjectCache recent) {
        this.geoServer = geoServer;
        this.recent = recent;
    }

    public Catalog catalog() {
        return geoServer.getCatalog();
    }

    protected GeoServerDataDirectory dataDir() {
        return new GeoServerDataDirectory(geoServer.getCatalog().getResourceLoader());
    }

    protected Integer offset(Integer page, Integer count) {
        return page != null ? page * (count != null ? count : DEFAULT_PAGESIZE) : null;
    }

    protected WorkspaceInfo findWorkspace(String wsName, Catalog cat) {
        WorkspaceInfo ws = cat.getWorkspaceByName(wsName);
        if (ws == null) {
            throw new NotFoundException(String.format("No such workspace %s", wsName));
        }
        return ws;
    }

    protected LayerInfo findLayer(String wsName, String name, Catalog cat) {
        LayerInfo l = cat.getLayerByName(wsName+":"+name);
        if( l == null ){
            throw new NotFoundException(String.format("No such layer %s:%s", wsName, name));
        }
        return l;
    }

    protected StoreInfo findStore(String wsName, String name, Catalog cat) {
        StoreInfo s = cat.getStoreByName(wsName, name, StoreInfo.class);
        if (s == null) {
            throw new NotFoundException(String.format("No such store %s:%s", wsName, name));
        }
        return s;
    }

    protected LayerGroupInfo findMap(String wsName, String name, Catalog cat) {
        LayerGroupInfo m = cat.getLayerGroupByName(wsName, name);
        if (m == null) {
            throw new NotFoundException(String.format("No such map %s:%s", wsName, name));
        }
        return m;
    }

    /**
     * Returns the namespace associated with the specified workspace.
     */
    protected NamespaceInfo namespaceFor(WorkspaceInfo ws) {
        return geoServer.getCatalog().getNamespaceByPrefix(ws.getName());
    }

    protected ServletFileUpload newFileUpload() {
        DiskFileItemFactory diskFactory = new DiskFileItemFactory();
        diskFactory.setSizeThreshold(1024*1024*256); // TODO: make this configurable

        return new ServletFileUpload(diskFactory);
    }

    protected FileItemIterator doFileUpload(final HttpServletRequest request) throws FileUploadException, IOException {
        final ServletFileUpload upload = newFileUpload();
        //Delegate FileItemIterator to only return files
        return new FileItemIterator() {
            FileItemIterator delegate = upload.getItemIterator(request);
            FileItemStream next = null;
            @Override
            public boolean hasNext() throws FileUploadException, IOException {
                if (next != null) {
                    return true;
                }
                while (delegate.hasNext()) {
                    FileItemStream item = delegate.next();
                    if (!item.isFormField()) {
                        next = item;
                        break;
                    }
                }
                return next != null;
            }

            @Override
            public FileItemStream next() throws FileUploadException,
                    IOException {
                if (hasNext()) {
                    FileItemStream current = next;
                    next = null;
                    return current;
                }
                throw new NoSuchElementException();
            }
        };
    }
    
    protected SortBy parseSort(String sort) {
        SortBy sortBy = null;
        if (sort != null) {
            String[] sortArr = sort.split(":", 2);
            if (sortArr.length == 2) {
                if (sortArr[1].equals("asc")) {
                    sortBy = Predicates.asc(sortArr[0]);
                } else if (sortArr[1].equals("desc")) {
                    sortBy = Predicates.desc(sortArr[0]);
                } else {
                    throw new BadRequestException("Sort order must be \"asc\" or \"desc\"");
                }
            } else {
                sortBy = Predicates.asc(sortArr[0]);
            }
        }
        return sortBy;
    }
}
