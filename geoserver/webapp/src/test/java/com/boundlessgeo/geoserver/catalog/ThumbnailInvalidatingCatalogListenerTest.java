package com.boundlessgeo.geoserver.catalog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static com.boundlessgeo.geoserver.catalog.ThumbnailInvalidatingCatalogListener.updateMetadataMap;

import org.geoserver.catalog.MetadataMap;
import org.junit.Test;

import com.boundlessgeo.geoserver.api.controllers.Metadata;

public class ThumbnailInvalidatingCatalogListenerTest {
    
    @Test
    public void testUpdateMetadataMap() {
        MetadataMap oldMap = new MetadataMap();
        MetadataMap newMap = new MetadataMap();
        
        //Not in either - No change to metadata map, but should continue invalidation
        assertFalse(updateMetadataMap(oldMap, newMap));
        
        //Only in newMap - Adding thumbnail metadata
        newMap.put(Metadata.THUMBNAIL, "test");
        
        assertTrue(updateMetadataMap(oldMap, newMap));
        
        //Both the same - Should remove from newMap
        oldMap.put(Metadata.THUMBNAIL, "test");
        
        assertFalse(updateMetadataMap(oldMap, newMap));
        assertFalse(newMap.containsKey(Metadata.THUMBNAIL));
        
        //Only in oldMap - removing thumbnail metadata
        assertTrue(updateMetadataMap(oldMap, newMap));
    }

}
