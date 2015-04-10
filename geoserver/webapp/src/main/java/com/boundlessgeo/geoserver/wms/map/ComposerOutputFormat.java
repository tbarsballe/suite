/* (c) 2015 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.wms.map;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.MapLayerInfo;
import org.geoserver.wms.MapProducerCapabilities;
import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSMapContent;
import org.geoserver.wms.WebMap;
import org.geoserver.wms.map.AbstractMapOutputFormat;
import org.geoserver.wms.map.RenderedImageMap;
import org.geoserver.wms.map.RenderedImageMapOutputFormat;
import org.geotools.util.logging.Logging;

import com.boundlessgeo.geoserver.AppConfiguration;
import com.boundlessgeo.geoserver.api.controllers.Metadata;

/**
 * Extension of the regular png map output format that also caches thumbnails and bounds whenever
 * produceMap is called. Thumbnail files are cached to the cacheDir specified by AppConfiguration.
 * The thumbnail location/filename and request bounds are saved to the metadata of the LayerGroupInfo 
 * or LayerInfo object that this request involves.
 * 
 * To specify a Layer Group for the request, the request.getFormatOptions() map should include the 
 * entry "layerGroup" with value equal to the Layer Group name. Requests with more then one layer 
 * but no Layer Group will not cache thumbnails or other metadata, since there is no single target
 * object to store this data.
 */
public class ComposerOutputFormat extends AbstractMapOutputFormat {
    
    AppConfiguration config;
    WMS wms;
    RenderedImageMapOutputFormat delegate;
    
    static final String FORMAT = "composer";
    static final String TYPE = "png";
    static final String MIME_TYPE = "image/png";
    public static final String EXTENSION = ".png";
    public static final String EXTENSION_HR = "@2x.png";
    public static final int THUMBNAIL_SIZE = 75;
    
    static final Set<String> outputFormatNames = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(new String[] { FORMAT })));
    
    /** A logger for this class. */
    private static final Logger LOGGER = Logging.getLogger(ComposerOutputFormat.class);
    
    public ComposerOutputFormat(WMS wms, AppConfiguration config) {
        super(MIME_TYPE);
        delegate = new RenderedImageMapOutputFormat(MIME_TYPE, new String[] {FORMAT}, wms);
        this.config = config;
        this.wms = wms;
    }
    
    //Only include format "composer" so as to not conflict with the regular PNG format
    @Override
    public Set<String> getOutputFormatNames() {
        return outputFormatNames;
    }
    
    /**
     * Produce the map. 
     * Create regular an high-res thumbnails by scaling the map image, and save these to the map/layer metadata
     * Save the request bounds to the map/layer metadata
     */
    @Override
    public final WebMap produceMap(WMSMapContent mapContent) throws ServiceException,
            IOException {
        return produceMap(mapContent, delegate.produceMap(mapContent));
    }
    public RenderedImageMap produceMap(final WMSMapContent mapContent, final boolean tiled) {
        return produceMap(mapContent, delegate.produceMap(mapContent, tiled));
    }
    private RenderedImageMap produceMap(final WMSMapContent mapContent, RenderedImageMap map) {
        final GetMapRequest request = mapContent.getRequest();
        
        Catalog catalog = wms.getCatalog();
        
        List<MapLayerInfo> mapLayers = request.getLayers();
        
        LayerInfo layer = null;
        LayerGroupInfo layerGroup = null;
        PublishedInfo info = null;
        
        Object lg = request.getFormatOptions().get("layerGroup");
        if (lg == null) {
            if (mapLayers.size() == 1) {
                layer = mapLayers.get(0).getLayerInfo();
                info = layer;
                
            } else if (mapLayers.size() > 1) {
                LOGGER.log(Level.WARNING, "Multiple layers but no layer group. Skipping thumbnails and metadata.");
                return map;
            } else {
                LOGGER.log(Level.WARNING, "No layers. Skipping thumbnails and metadata.");
                return map;
                
            }
        } else {
            layerGroup = catalog.getLayerGroupByName(lg.toString());
            if (layerGroup == null) {
                LOGGER.log(Level.WARNING, "Layer group "+lg+" does not exist. Skipping thumbnails and metadata.");
                return map;
            }
            info = layerGroup;
        }
        
        //Create and save the thumbnails
        try {
            RenderedImage image = map.getImage();
            double scale = ((double)THUMBNAIL_SIZE) / 
                    Math.min((double)image.getWidth(), (double)image.getHeight());
            
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(image, TYPE, os);
            
            FileOutputStream loRes = null;
            FileOutputStream hiRes = null;
            try {
                loRes = new FileOutputStream(config.createCacheFile(thumbnailFilename(info)).getPath());
                hiRes = new FileOutputStream(config.createCacheFile(thumbnailFilename(info, true)).getPath());
                
                loRes.write(scaleImage(os.toByteArray(), scale, true));
                hiRes.write(scaleImage(os.toByteArray(), 2.0*scale, true));
            } finally {
                if (loRes != null) { loRes.close(); }
                if (hiRes != null) { hiRes.close(); }
            }
            Metadata.thumbnail(info, thumbnailFilename(info));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write thumbnails.");
        }
        
        //Save current bbox to metadata
        Metadata.bbox(info, request.getBbox());
        
        return map;
    }
    
    /**
     * Utility method to generate a consistent thumbnail filename
     * @param layer to create the filename for
     * @return relative filename
     */
    public static final String thumbnailFilename(PublishedInfo layer) {
        return thumbnailFilename(layer, false);
    }
    
    /**
     * Utility method to generate a consistent thumbnail filename
     * @param layer to create the filename for
     * @param hiRes is this the name of a hi-res thumbnail file?
     * @return relative filename
     */
    public static final String thumbnailFilename(PublishedInfo layer, boolean hiRes) {
        if (hiRes) {
            return layer.getId()+EXTENSION_HR;
        }
        return layer.getId()+EXTENSION;
    }
    
    public static final byte[] scaleImage(byte[] in, double scale) throws IOException {
        return scaleImage(in, scale, false);
    }
    /**
     * Utility method for scaling thumbnails. Scales byte[] image by a scale factor.
     * Optionally crops images to square.
     * @param in byte[]m containing the input image
     * @param scale Scale amount
     * @param square Boolean flag to crop to a square image
     * @return byte[] contianing the transformed image
     * @throws IOException
     */
    public static final byte[] scaleImage(byte[] in, double scale, boolean square) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(in));
        BufferedImage scaled = image;
        if (scale != 1.0) {
            AffineTransform scaleTransform = AffineTransform.getScaleInstance(scale, scale);
            AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BILINEAR);
            scaled =  bilinearScaleOp.filter(image, new BufferedImage(
                    (int)(image.getWidth()*scale), (int)(image.getHeight()*scale), image.getType()));
        }
        if (square) {
            if (scaled.getHeight() > scaled.getWidth()) {
                scaled = scaled.getSubimage(0, (scaled.getHeight() - scaled.getWidth())/2, 
                                            scaled.getWidth(), scaled.getWidth());
            } else if (scaled.getHeight() < scaled.getWidth()) {
                scaled = scaled.getSubimage((scaled.getWidth() - scaled.getHeight())/2, 0,
                                            scaled.getHeight(), scaled.getHeight());
            }
        }
        
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(scaled, TYPE, os);
        return os.toByteArray();
    }
    
    @Override
    public MapProducerCapabilities getCapabilities(String format) {
        return delegate.getCapabilities(format);
    }
}
