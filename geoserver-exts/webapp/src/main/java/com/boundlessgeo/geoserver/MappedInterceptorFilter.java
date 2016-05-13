package com.boundlessgeo.geoserver;

import org.geoserver.platform.ExtensionFilter;
import org.springframework.web.servlet.handler.MappedInterceptor;

public class MappedInterceptorFilter implements ExtensionFilter {
    @Override
    public boolean exclude(String beanId, Object bean) {
        return bean instanceof MappedInterceptor;
    }
}
