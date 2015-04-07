package com.boundlessgeo.geoserver.wms.map;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Paths;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resources;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.MapLayerInfo;
import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSMapContent;
import org.geoserver.wms.map.RenderedImageMap;
import org.geoserver.wms.map.RenderedImageMapOutputFormat;
import org.geotools.util.logging.Logging;

import com.boundlessgeo.geoserver.api.controllers.Metadata;

public class ComposerOutputFormat extends RenderedImageMapOutputFormat {
    
    static final String FORMAT = "composer";
    static final String TYPE = "png";
    static final String MIME_TYPE = "image/png";
    public static final String EXTENSION = ".png";
    public static final String EXTENSION_HR = "@2x.png";
    public static final int THUMBNAIL_SIZE = 175;
    
    static final Set<String> outputFormatNames = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(new String[] { FORMAT })));
    
    /** A logger for this class. */
    private static final Logger LOGGER = Logging.getLogger(ComposerOutputFormat.class);
    
    public ComposerOutputFormat(WMS wms) {
        super(MIME_TYPE, new String[] {FORMAT}, wms);
    }
    
    @Override
    public Set<String> getOutputFormatNames() {
        return outputFormatNames;
    }
    
    @Override
    public RenderedImageMap produceMap(final WMSMapContent mapContent, final boolean tiled) {
        RenderedImageMap map = super.produceMap(mapContent, tiled);
        final GetMapRequest request = mapContent.getRequest();
        
        Catalog catalog = wms.getCatalog();
        GeoServerResourceLoader rl = catalog.getResourceLoader();
        
        List<MapLayerInfo> mapLayers = request.getLayers();
        
        LayerInfo layer = null;
        LayerGroupInfo layerGroup = null;
        PublishedInfo info = null;
        
        Resource thumbnailDir = null;
        
        Object lg = request.getFormatOptions().get("layerGroup");
        if (lg == null) {
            if (mapLayers.size() == 1) {
                layer = mapLayers.get(0).getLayerInfo();
                info = layer;
                thumbnailDir = thumbnailDirectory(layer, rl);
                
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
            thumbnailDir = thumbnailDirectory(layerGroup, rl);
        }
        
        //Create and save the thumbnails
        try {
            RenderedImage image = map.getImage();
            double scale = ((double)THUMBNAIL_SIZE) / 
                    Math.min((double)image.getWidth(), (double)image.getHeight());
            
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(image, TYPE, os);
            
            InputStream hiRes = scaleImage(new ByteArrayInputStream(os.toByteArray()), 2.0*scale);
            InputStream loRes = scaleImage(new ByteArrayInputStream(os.toByteArray()), scale);
            
            //Write the stream to a Resource
            String filename = info.getName();
            Resources.copy(loRes, thumbnailDir, thumbnailFilename(info));
            Resources.copy(hiRes, thumbnailDir, thumbnailFilename(info, true));
            
            Resource thumbnail = thumbnailDir.get(thumbnailFilename(info));
            Metadata.thumbnail(info, thumbnail);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write thumbnails.");
        }
        
        
        //Save current bbox to metadata
        Metadata.bbox(info, request.getBbox());
        
        return map;
    }
    
    public static final Resource thumbnailDirectory(PublishedInfo layer, GeoServerResourceLoader rl) {
        if (layer instanceof LayerInfo) {
            WorkspaceInfo ws = ((LayerInfo)layer).getResource().getStore().getWorkspace();
            return rl.get(Paths.path("workspaces",ws.getName(),ws.getName(),layer.getName()));
        } else if (layer instanceof LayerGroupInfo) {
            WorkspaceInfo ws = ((LayerGroupInfo)layer).getWorkspace();
            return rl.get(Paths.path("workspaces",ws.getName(),"layergroups"));
        }
        return null;
    }
    
    //Produce a consistent filename for thumbnails
    public static final String thumbnailFilename(PublishedInfo layer) {
        return thumbnailFilename(layer, false);
    }
    
    public static final String thumbnailFilename(PublishedInfo layer, boolean hiRes) {
        if (hiRes) {
            return layer.getId()+EXTENSION_HR;
        }
        return layer.getId()+EXTENSION;
    }
    
    public static final InputStream scaleImage(InputStream in, double scale) throws IOException {
        BufferedImage image = ImageIO.read(in);
        
        AffineTransform scaleTransform = AffineTransform.getScaleInstance(scale, scale);
        AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BILINEAR);
        
        BufferedImage scaled =  bilinearScaleOp.filter(image,
            new BufferedImage((int)(image.getWidth()*scale), (int)(image.getHeight()*scale), image.getType()));
        
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(scaled, TYPE, os);
        return new ByteArrayInputStream(os.toByteArray());
    }
}
