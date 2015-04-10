package com.boundlessgeo.geoserver.wms.map;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;

import org.geoserver.platform.ServiceException;
import org.geoserver.wms.MapProducerCapabilities;
import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSMapContent;
import org.geoserver.wms.map.PNGMapResponse;
import org.geoserver.wms.map.RenderedImageMapResponse;

public class ComposerMapResponse extends RenderedImageMapResponse {
    
    PNGMapResponse delegate;
    private static final String MIME_TYPE = "image/png";
    private static final String[] OUTPUT_FORMATS = { "composer" };
    
    public ComposerMapResponse(WMS wms) {
            super(OUTPUT_FORMATS, wms);
            delegate = new PNGMapResponse(wms);
        }
    
    @Override
    public void formatImageOutputStream(RenderedImage image,
            OutputStream outStream, WMSMapContent mapContent)
            throws ServiceException, IOException {
        delegate.formatImageOutputStream(image, outStream, mapContent);
    
    }
    
    @Override
    public MapProducerCapabilities getCapabilities(String outputFormat) {
        return delegate.getCapabilities(outputFormat);
    }
}
