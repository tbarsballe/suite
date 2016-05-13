package com.boundlessgeo.geowebcache.web;

import java.net.URL;

import org.geowebcache.rest.webresources.WebResourceBundle;

public class BoundlessWebResourceBundle implements WebResourceBundle {

	@Override
	public URL apply(String t) {
		return BoundlessWebResourceBundle.class.getResource(t);
	}

}
