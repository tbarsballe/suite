/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Iterator;

import org.geoserver.catalog.SLDHandler;
import org.geoserver.catalog.StyleHandler;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resource.Type;
import org.geoserver.ysld.YsldHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.boundlessgeo.geoserver.api.converters.JSONMessageConverter;
import com.boundlessgeo.geoserver.api.converters.ResourceMessageConverter;
import com.boundlessgeo.geoserver.api.converters.YsldMessageConverter;
import com.boundlessgeo.geoserver.json.JSONArr;
import com.boundlessgeo.geoserver.json.JSONObj;
import com.boundlessgeo.geoserver.json.JSONWrapper;

public class IconControllerTest {

    @Mock
    GeoServer geoServer;

    @InjectMocks
    IconController ctrl;

    MockMvc mvc;

    @Before
    public void setUpAppContext() {
        WebApplicationContext appContext = mock(WebApplicationContext.class);
        when(appContext.getBeanNamesForType(StyleHandler.class)).thenReturn(new String[]{"ysldHandler","sldHandler"});
        when(appContext.getBean("ysldHandler")).thenReturn(new YsldHandler());
        when(appContext.getBean("sldHandler")).thenReturn(new SLDHandler());

        new GeoServerExtensions().setApplicationContext(appContext);
    }

    @Before
    public void setUpUpContextAndMVC() {
        MockitoAnnotations.initMocks(this);
        mvc = MockMvcBuilders.standaloneSetup( ctrl )
            .setMessageConverters(
                new JSONMessageConverter(), new ResourceMessageConverter(),
                new YsldMessageConverter(), new ByteArrayHttpMessageConverter())
            .build();
    }


    @Test
    public void testList() throws Exception {
        MockGeoServer.get().catalog()
        .resources()
          .resource("workspaces/foo/styles/one.yaml", "title: raw")
          .directory("workspaces/foo/styles")
          .resource("workspaces/foo/styles/icon.png", "PNG")
          .resource("workspaces/foo/styles/symbols.TTF", "TTF")
          .directory("styles")
          .resource("styles/global.png", "PNG")
        .geoServer().catalog()
          .workspace("foo", "http://scratch.org", true)
            .layer("one")
              .style().ysld("one.yaml")
        .geoServer().build(geoServer);

      // Test directory placeholder
      GeoServerResourceLoader rl = this.geoServer.getCatalog().getResourceLoader();
      Resource d = rl.get("workspaces/foo/styles");
      assertEquals( d.getType(), Type.DIRECTORY );
      assertEquals( 3, d.list().size() );
      
      MvcResult result = mvc.perform(get("/api/icons/list/foo"))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn();

      JSONArr arr = JSONWrapper.read(result.getResponse().getContentAsString()).toArray();
      assertEquals( 2, arr.size() );
      for( Iterator<Object> i = arr.iterator(); i.hasNext();){
          Object item = i.next();
          if( item instanceof JSONObj){
              JSONObj obj = (JSONObj) item;
              if(obj.str("name").equals("icon.png")){
                  assertEquals( "png", obj.get("format"));
                  assertEquals( "image/png", obj.get("mime"));
                  assertTrue( obj.str("url").endsWith("icon.png"));            
              }
          }
      }
      
      //Global styles dir
      d = rl.get("styles");
      assertEquals( d.getType(), Type.DIRECTORY );
      assertEquals( 1, d.list().size() );
      
      result = mvc.perform(get("/api/icons/list"))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andReturn();

      arr = JSONWrapper.read(result.getResponse().getContentAsString()).toArray();
      assertEquals( 1, arr.size() );
    }

    @Test
    public void testRaw() throws Exception {
        MockGeoServer.get().catalog()
        .resources()
          .resource("workspaces/foo/styles/one.yaml", "title: raw")
          .directory("workspaces/foo/styles")
          .resource("workspaces/foo/styles/icon.png", "PNG8")
          .resource("workspaces/foo/styles/symbols.TTF", "TTF")
          .directory("styles")
          .resource("styles/global.png", "PNG")
        .geoServer().catalog()
          .workspace("foo", "http://scratch.org", true)
            .layer("one")
              .style().ysld("one.yaml")
        .geoServer().build(geoServer);

      MvcResult result = mvc.perform(get("/api/icons/foo/icon.png"))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.IMAGE_PNG_VALUE))
              .andReturn();

      String raw = result.getResponse().getContentAsString();
      
      assertEquals("PNG8",raw);
      
      //Global styles directory
      result = mvc.perform(get("/api/icons/global.png"))
              .andExpect(status().isOk())
              .andExpect(content().contentType(MediaType.IMAGE_PNG_VALUE))
              .andReturn();

      raw = result.getResponse().getContentAsString();
      
      assertEquals("PNG",raw);
      
    }

    
}
