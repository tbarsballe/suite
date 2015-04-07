/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import org.geoserver.catalog.Info;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.ows.util.OwsUtils;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Resource;
import org.geotools.util.Converters;

import com.vividsolutions.jts.geom.Envelope;

import java.util.Date;

/**
 * Utility class for dealing with api specific metadata.
 */
public class Metadata {

    static final String CREATED = "created";
    static final String MODIFIED = "modified";
    static final String IMPORTED = "imported";
    
    public static final String THUMBNAIL = "thumbnail";
    static final String BBOX = "bbox";
    
    public static void bbox(PublishedInfo obj, Envelope bbox) {
        map(obj).put(BBOX, bbox);
    }
    
    public static Envelope bbox(PublishedInfo obj) {
        return Converters.convert(map(obj).get(BBOX), Envelope.class);
    }
    
    public static void thumbnail(PublishedInfo obj, Resource thumbnail) {
        map(obj).put(THUMBNAIL, thumbnail.path());
    }
    
    public static Resource thumbnail(PublishedInfo obj, GeoServerResourceLoader rl) {
        Object path = map(obj).get(THUMBNAIL);
        if (path == null) {
            return null;
        }
        return rl.get(path.toString());
    }
    
    public static void invalidateThumbnail(PublishedInfo layer) {
        //Allow a bit of leeway, to support GetMap composer format 
        //(in case of getMap returning before put layer)
        Date d = new Date(new Date().getTime()-1000);
        if (Metadata.modified(layer) != null && d.before(Metadata.modified(layer))) {
            return;
        }
        Metadata.map(layer).remove(Metadata.THUMBNAIL);
    }
    
    public static void created(Info obj, Date created) {
        MetadataMap map = map(obj);
        map.put(CREATED, created);
        map.put(MODIFIED, created);
    }

    public static Date created(Info obj) {
        return Converters.convert(map(obj).get(CREATED), Date.class);
    }

    public static void modified(Info obj, Date created) {
        map(obj).put(MODIFIED, created);
    }

    public static Date modified(Info obj) {
        return Converters.convert(map(obj).get(MODIFIED), Date.class);
    }

    static MetadataMap map(Info obj) {
        Object map = OwsUtils.get(obj, "metadata");
        if (map != null && map instanceof MetadataMap) {
            return (MetadataMap) map;
        }
        return new MetadataMap();
    }
}
