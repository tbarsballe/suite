/* (c) 2015 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupHelper;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.MapLayerInfo;
import org.geoserver.wms.WebMap;
import org.geoserver.wms.WebMapService;
import org.geoserver.wms.map.RenderedImageMap;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.styling.Style;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.boundlessgeo.geoserver.AppConfiguration;
import com.boundlessgeo.geoserver.wms.map.ComposerOutputFormat;
import com.vividsolutions.jts.geom.Envelope;

@Controller
@RequestMapping("/api/thumbnails")
public class ThumbnailController extends ApiController {
    
    static final String TYPE = "png";
    static final String MIME_TYPE = "image/png";
    
    @Autowired
    @Qualifier("wmsServiceTarget")
    WebMapService wms;
    
    @Autowired
    AppConfiguration config;
    
    @Autowired
    public ThumbnailController(GeoServer geoServer) {
        super(geoServer);
    }
    
    /**
     * Endpoint to retrieve a cached thumbnail for a map, generating it if it does not exist.
     * If the request parameter "hiRes=true" is provided, returns a high-resolution (x2) thumbnail.
     * @param wsName Workspace name
     * @param name Map name
     * @param hiRes Whether or not to return a high-res thumbnail. Optional, defaults to false
     * @param request
     * @return HttpEntity containing the thumbnail image as a byte array
     * @throws Exception
     */
    @RequestMapping(value = "/maps/{wsName:.+}/{name:.+}", method = RequestMethod.GET)
    public HttpEntity<byte[]> getMap(@PathVariable String wsName, 
            @PathVariable String name, 
            @RequestParam(value="hiRes", required=false, defaultValue="false") Boolean hiRes,
            HttpServletRequest request) throws Exception {
        
        Catalog catalog = geoServer.getCatalog();
        WorkspaceInfo ws = findWorkspace(wsName, catalog);
        LayerGroupInfo map = findMap(wsName, name, catalog);
        
        return get(ws, map, hiRes);
    }
    
    /**
     * Endpoint to retrieve a cached thumbnail for a layer, generating it if it does not exist.
     * If the request parameter "hiRes=true" is provided, returns a high-resolution (x2) thumbnail.
     * @param wsName Workspace name
     * @param name Layer name
     * @param hiRes Whether or not to return a high-res thumbnail. Optional, defaults to false
     * @param request
     * @return HttpEntity containing the thumbnail image as a byte array
     * @throws Exception
     */
    @RequestMapping(value = "/layers/{wsName:.+}/{name:.+}", method = RequestMethod.GET)
    public HttpEntity<byte[]> getLayer(@PathVariable String wsName, 
            @PathVariable String name, 
            @RequestParam(value="hiRes", required=false, defaultValue="false") Boolean hiRes,
            HttpServletRequest request) throws Exception {
        
        Catalog catalog = geoServer.getCatalog();
        WorkspaceInfo ws = findWorkspace(wsName, catalog);
        LayerInfo layer = findLayer(wsName, name, catalog);
        
        return get(ws, layer, hiRes);
    }
    
    /**
     * Retrieve or create the thumbnail for a PublishedInfo
     * @param ws Workspace for the layer
     * @param layer LayerInfo or LayerGroupInfo to get the thumbnail of
     * @param hiRes Flag to return hi-res (x2) thumbnail
     * @return HttpEntity containing the thumbnail image as a byte array
     * @throws Exception
     */
    public HttpEntity<byte[]> get(WorkspaceInfo ws, PublishedInfo layer, boolean hiRes) throws Exception {
        String path = Metadata.thumbnail(layer);
        FileInputStream in = null;
        
        //Has not been generated yet, use WMS reflector
        if (path == null) {
            createThumbnail(ws, layer);
            path = Metadata.thumbnail(layer);
        }
        try {
            File thumbnailFile;
            if (hiRes) {
                thumbnailFile = config.getCacheFile(path.replaceAll(
                        ComposerOutputFormat.EXTENSION+"$", ComposerOutputFormat.EXTENSION_HR));
            } else {
                thumbnailFile = config.getCacheFile(path);
            }
            in = new FileInputStream(thumbnailFile);
            byte[] bytes = IOUtils.toByteArray(in);
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(MIME_TYPE));
            headers.setLastModified(thumbnailFile.lastModified());
            return new HttpEntity<byte[]>(bytes, headers);
        } finally {
            if (in != null) { in.close(); }
        }
    }
    
    /**
     * Creates a thumbnail for the layer as a Resource, and updates the layer with the new thumbnail
     * @param ws The workspace of the layer
     * @param layer The layer or layerGroup to get the thumbnail for
     * @return The thumbnail image as a Resource
     * @throws Exception
     */
    protected void createThumbnail(WorkspaceInfo ws, PublishedInfo layer) throws Exception {
        Catalog catalog = geoServer.getCatalog();
        
        //Set up getMap request
        GetMapRequest request = new GetMapRequest();
        
        List<MapLayerInfo> layers = new ArrayList<MapLayerInfo>();
        List<Style> styles = new ArrayList<Style>();
        Envelope bbox = null;
        if (layer instanceof LayerInfo) {
            layers.add(new MapLayerInfo((LayerInfo)layer));
            styles.add(((LayerInfo)layer).getDefaultStyle().getStyle());
            bbox = ((LayerInfo)layer).getResource().boundingBox();
        } else if (layer instanceof LayerGroupInfo) {
            LayerGroupHelper helper = new LayerGroupHelper((LayerGroupInfo)layer);
            bbox = ((LayerGroupInfo)layer).getBounds();
            for (LayerInfo l : helper.allLayersForRendering()) {
                layers.add(new MapLayerInfo(l));
            }
            for (StyleInfo s : helper.allStylesForRendering()) {
                styles.add(s.getStyle());
            }
        } else {
            throw new RuntimeException("layer must be one of LayerInfo or LayerGroupInfo");
        }
        request.setLayers(layers);
        request.setStyles(styles);
        request.setFormat(MIME_TYPE);
        
        //If the last used bbox has been saved, use that
        if (Metadata.bbox(layer) != null) {
            bbox = Metadata.bbox(layer);
        }
        //Set the size of the HR thumbnail
        //Take the smallest bbox dimension as the min dimension. We can then crop the other 
        //dimension to give a square thumbnail
        request.setBbox(bbox);
        if (bbox.getWidth() < bbox.getHeight()) {
            request.setWidth(2*ComposerOutputFormat.THUMBNAIL_SIZE);
        } else {
            request.setHeight(2*ComposerOutputFormat.THUMBNAIL_SIZE);
        }
        
        //Run the getMap request through the WMS Reflector
        WebMap response = wms.reflect(request);
        
        //Get the resulting map as a stream
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        if (response instanceof RenderedImageMap) {
            RenderedImageMap map = (RenderedImageMap)response;
            RenderedImage image = map.getImage();
            ImageIO.write(image, TYPE, os);
        } else {
            throw new RuntimeException("Unsupported getMap response format:" + response.getClass().getName());
        }
        
        //Write the thumbnail files
        FileOutputStream loRes = null;
        FileOutputStream hiRes = null;
        try {
            loRes = new FileOutputStream(config.createCacheFile(ComposerOutputFormat.thumbnailFilename(layer)).getPath());
            hiRes = new FileOutputStream(config.createCacheFile(ComposerOutputFormat.thumbnailFilename(layer, true)).getPath());
            
            loRes.write(ComposerOutputFormat.scaleImage(os.toByteArray(), 0.5, true));
            //Don't scale, but crop to square
            hiRes.write(ComposerOutputFormat.scaleImage(os.toByteArray(), 1.0, true));
        } finally {
            if (loRes != null) { loRes.close(); }
            if (hiRes != null) { hiRes.close(); }
        }
        Metadata.thumbnail(layer, ComposerOutputFormat.thumbnailFilename(layer));
        
        if (layer instanceof LayerInfo) {
            catalog.save((LayerInfo)layer);
        } else if (layer instanceof LayerGroupInfo) {
            catalog.save((LayerGroupInfo)layer);
        }
    }
}
