/* (c) 2014-2015 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.catalog;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogException;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.event.CatalogAddEvent;
import org.geoserver.catalog.event.CatalogListener;
import org.geoserver.catalog.event.CatalogModifyEvent;
import org.geoserver.catalog.event.CatalogPostModifyEvent;
import org.geoserver.catalog.event.CatalogRemoveEvent;

import com.boundlessgeo.geoserver.api.controllers.Metadata;

public class ThumbnailInvalidatingCatalogListener implements CatalogListener {
    Catalog catalog;
    //Prevent recursion
    private boolean modifying = false;
    
    public ThumbnailInvalidatingCatalogListener(Catalog catalog) {
        this.catalog = catalog;
        catalog.addListener(this);
    }
    
    @Override
    public void handleAddEvent(CatalogAddEvent event) throws CatalogException {
        //No change on add, as thumbnails will not have been generated yet
    }

    @Override
    public void handleRemoveEvent(CatalogRemoveEvent event)
            throws CatalogException {
        //TODO: Delete thumbnails?
        CatalogInfo source = event.getSource();
        
        //If we delete a style or layer used by a layer or map, invalidate upstream thumbnails
        if (!modifying) {
            try {
                modifying = true;
                if (source instanceof StyleInfo) {
                    //Invalidate any maps or layers using this style
                    for (LayerInfo layer : catalog.getLayers()) {
                        if (source.equals(layer.getDefaultStyle())) {
                            Metadata.invalidateThumbnail(layer);
                            catalog.save(layer);
                        }
                    }
                    for (LayerGroupInfo layerGroup : catalog.getLayerGroups()) {
                        if (layerGroup.getStyles().contains(source)) {
                            Metadata.invalidateThumbnail(layerGroup);
                            catalog.save(layerGroup);
                        }
                    }
                } else if(source instanceof LayerInfo) {
                    //Invalidate any maps using this layer
                    for (LayerGroupInfo layerGroup : catalog.getLayerGroups()) {
                        if (layerGroup.getLayers().contains(source)) {
                            Metadata.invalidateThumbnail(layerGroup);
                            catalog.save(layerGroup);
                        }
                    }
                }
            } finally {
                modifying = false;
            }
        }
    }

    /* Invalidates all thumbnails that depend on the modified resources, by removing the thumbnail 
     * entry from the associated metadata map. Only affects LayerInfo and LayerGroupInfo objects, 
     * and only runs for StyleInfo, LayerInfo, or LayerGroupInfo objects. 
     * If the event updates the thumbnail entry, this new value is preserved for the source object, 
     * but the thumbnails for all dependent objects are still invalidated
     */
    @Override
    public void handleModifyEvent(CatalogModifyEvent event)
            throws CatalogException {
        //Handle all modifications postModify, so metadata is updated correctly
        
        CatalogInfo source = event.getSource();
        
        if (!modifying) {
            try {
                modifying = true;
                if (source instanceof StyleInfo) {
                    //Invalidate any maps or layers using this style
                    for (LayerInfo layer : catalog.getLayers()) {
                        if (source.equals(layer.getDefaultStyle())) {
                            Metadata.invalidateThumbnail(layer);
                            catalog.save(layer);
                        }
                    }
                    for (LayerGroupInfo layerGroup : catalog.getLayerGroups()) {
                        if (layerGroup.getStyles().contains(source)) {
                            Metadata.invalidateThumbnail(layerGroup);
                            catalog.save(layerGroup);
                        }
                    }
                } else if(source instanceof LayerInfo) {
                    //Invalidate layer, plus any maps using this layer
                    if (!updateThumbnailEvent(event)) {
                        LayerInfo layer = catalog.getLayer(source.getId());
                        Metadata.invalidateThumbnail(layer);
                        catalog.save(layer);
                    }
                    for (LayerGroupInfo layerGroup : catalog.getLayerGroups()) {
                        if (layerGroup.getLayers().contains(source)) {
                            Metadata.invalidateThumbnail(layerGroup);
                            catalog.save(layerGroup);
                        }
                    }
                } else if (source instanceof LayerGroupInfo) {
                    //Only invalidate map
                    if (!updateThumbnailEvent(event)) {
                        LayerGroupInfo layerGroup = catalog.getLayerGroup(source.getId());
                        Metadata.invalidateThumbnail(layerGroup);
                        catalog.save(layerGroup);
                    }
                }
            } finally {
                modifying = false;
            }
        }
        
    }
    
    /* Determines if this event changes the thumbnail entry in the metadata map. If so returns true,
     * otherwise returns false.
     * 
     * If the event modifies the metadata map, and the metadata map contains a thumbnail entry that 
     * is not changed, invalidates the thumbnail entry by removing it from the metadata map.
     * 
     */
    private boolean updateThumbnailEvent(CatalogModifyEvent event) {
        for (int i = 0; i < event.getNewValues().size(); i++) {
            if (event.getNewValues().get(i) instanceof MetadataMap) {
                MetadataMap oldMap = (MetadataMap)event.getOldValues().get(i);
                MetadataMap newMap = (MetadataMap)event.getNewValues().get(i);
                
                //Modify thumbnail entry
                if (oldMap.containsKey(Metadata.THUMBNAIL) && newMap.containsKey(Metadata.THUMBNAIL)
                        && oldMap.get(Metadata.THUMBNAIL) != newMap.get(Metadata.THUMBNAIL) ) {
                    return true;
                }
                //Add or remove thumbnail entry
                if ((oldMap.containsKey(Metadata.THUMBNAIL) && !newMap.containsKey(Metadata.THUMBNAIL))
                        || (!oldMap.containsKey(Metadata.THUMBNAIL) && newMap.containsKey(Metadata.THUMBNAIL))) {
                    return true;
                }
                //If the metadata map changes but the thumbnail does not, remove the thumbnail 
                //from the map to invalidate it.
                if (oldMap.containsKey(Metadata.THUMBNAIL) && newMap.containsKey(Metadata.THUMBNAIL)
                        && oldMap.get(Metadata.THUMBNAIL) == newMap.get(Metadata.THUMBNAIL) ) {
                    newMap.remove(Metadata.THUMBNAIL);
                }
            }
        }
        return false;
    }

    @Override
    public void handlePostModifyEvent(CatalogPostModifyEvent event)
            throws CatalogException { 
        
        
    }

    @Override
    public void reloaded() { }

}
