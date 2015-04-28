/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import java.util.Comparator;
import java.util.Iterator;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import com.boundlessgeo.geoserver.api.exceptions.BadRequestException;
import com.boundlessgeo.geoserver.util.RecentObjectCache;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.catalog.util.CloseableIteratorAdapter;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerDataDirectory;
import org.geoserver.ows.util.OwsUtils;

import com.boundlessgeo.geoserver.api.exceptions.NotFoundException;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.sort.SortOrder;

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

    @SuppressWarnings("unchecked")
    protected Iterator<FileItem> doFileUpload(HttpServletRequest request) throws FileUploadException {
        ServletFileUpload upload = newFileUpload();

        // filter out only file fields
        return Iterables.filter(upload.parseRequest(request), new Predicate<FileItem>() {
            @Override
            public boolean apply(@Nullable FileItem input) {
            return !input.isFormField() && input.getName() != null;
            }
        }).iterator();
    }

    protected <T extends CatalogInfo> CloseableIterator<T> iterator(Class<T> info, final Integer page, final Integer count, String sort, Filter filter) {
        Catalog cat = geoServer.getCatalog();
        
        final SortBy sortBy = parseSort(sort);
        //If we have an invalid property, assume it is in the metadata map
        if (sortBy != null && OwsUtils.getter( info.getClass(), sortBy.getPropertyName().getPropertyName(), null ) == null) {
            //Make a comparator for the metadata property
            Ordering<T> ordering = Ordering.from(new Comparator<T>() {
                @Override
                public int compare(T o1, T o2) {
                    Object v1 = Metadata.map(o1).get(sortBy.getPropertyName().getPropertyName());
                    Object v2 = Metadata.map(o2).get(sortBy.getPropertyName().getPropertyName());
                    if (v1 == null) {
                        if (v2 == null) {
                            return 0;
                        } else {
                            return -1;
                        }
                    } else if (v2 == null) {
                        return 1;
                    }
                    Comparable c1 = (Comparable) v1;
                    Comparable c2 = (Comparable) v2;
                    return c1.compareTo(c2);
                }
            });
            //Match asc/desc
            if (SortOrder.DESCENDING.equals(sortBy.getSortOrder())) {
                ordering = ordering.reverse();
            }
            
            //Extract the values from the catalog, and create a custom iterator
            try ( CloseableIterator<T> i = (CloseableIterator<T>)cat.list(info, filter) ) {
                //Apply the ordering
                Iterable<T> iterable = ordering.sortedCopy(Lists.newArrayList(i));
                //Apply page and count
                Integer offset = offset(page, count);
                if (offset != null && offset.intValue() > 0) {
                    iterable = Iterables.skip(iterable, offset.intValue());
                }
                if (count != null && count.intValue() >= 0) {
                    iterable = Iterables.limit(iterable, count.intValue());
                }
                return new CloseableIteratorAdapter<T>(iterable.iterator());
            }
        }
        return (CloseableIterator<T>) cat.list(info, filter, offset(page, count), count, sortBy);
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
