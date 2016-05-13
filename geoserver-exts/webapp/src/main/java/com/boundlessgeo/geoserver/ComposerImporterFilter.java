package com.boundlessgeo.geoserver;

import org.geoserver.platform.ExtensionFilter;

public class ComposerImporterFilter implements ExtensionFilter {
    //Hide the composer-importer bean from GeoServer to avoid conflicts
    @Override
    public boolean exclude(String beanId, Object bean) {
        return "composer-importer".equals(beanId);
    }
}
