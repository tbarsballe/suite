/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import com.boundlessgeo.geoserver.api.converters.JSONMessageConverter;
import com.boundlessgeo.geoserver.api.converters.ResourceMessageConverter;
import com.boundlessgeo.geoserver.api.converters.YsldMessageConverter;
import com.boundlessgeo.geoserver.json.JSONArr;
import com.boundlessgeo.geoserver.json.JSONObj;
import com.boundlessgeo.geoserver.json.JSONWrapper;
import com.boundlessgeo.geoserver.util.RecentObjectCache;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.SLDHandler;
import org.geoserver.catalog.StyleHandler;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.util.CloseableIteratorAdapter;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.resource.Resource;
import org.geoserver.ysld.YsldHandler;
import org.geotools.styling.NamedLayer;
import org.geotools.styling.PointSymbolizer;
import org.geotools.styling.Style;
import org.geotools.styling.StyledLayerDescriptor;
import org.geotools.ysld.Ysld;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opengis.filter.sort.SortBy;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class LayerControllerTest {

    @Mock
    GeoServer geoServer;

    @Mock
    RecentObjectCache recent;

    @InjectMocks
    LayerController ctrl;

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
            .workspace("foo", "http://scratch.org", true)
                .layer("one").style().point().layer()
                  .featureType().defaults().store("one").workspace()
                .layer("two").style().point().layer()
                  .featureType().defaults().store("two").workspace()
            .geoServer().build(geoServer);

        Catalog catalog = geoServer.getCatalog();
        final List<LayerInfo> layers = catalog.getLayers();
        Answer<CloseableIteratorAdapter<LayerInfo>> a = new Answer<CloseableIteratorAdapter<LayerInfo>>() {
            @Override
            public CloseableIteratorAdapter<LayerInfo> answer(InvocationOnMock invocation) throws Throwable {
                return new CloseableIteratorAdapter<LayerInfo>(layers.iterator());
            }
        };
        
        when(catalog.list(eq(LayerInfo.class), eq(Predicates.and(Predicates.equal("resource.namespace.prefix",
                "foo"), Predicates.fullTextSearch("o"))), isA(Integer.class), isA(Integer.class), isA(SortBy.class))).thenAnswer(a);
        
        when(catalog.count(eq(LayerInfo.class), eq(Predicates.and(Predicates.equal("resource.namespace.prefix",
                "foo"), Predicates.fullTextSearch(""))))).thenReturn(layers.size());
        
        MvcResult result = mvc.perform(get("/api/layers/foo"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        JSONObj obj = JSONWrapper.read(result.getResponse().getContentAsString()).toObject();
        assertEquals(2, obj.integer("total").intValue());
        assertEquals(2, obj.integer("count").intValue());
        assertEquals(0, obj.integer("page").intValue());

        JSONArr arr = obj.array("layers");
        assertEquals(arr.size(), 2);

        Iterables.find(arr, new Predicate<Object>() {
            @Override
            public boolean apply(@Nullable Object o) {
                return "one".equals(JSONWrapper.wrap(o).toObject().str("name"));
            }
        });
        Iterables.find(arr, new Predicate<Object>() {
            @Override
            public boolean apply(@Nullable Object o) {
                return "two".equals(JSONWrapper.wrap(o).toObject().str("name"));
            }
        });
        
        //Meaningless to test sort order here, since iterator is provided by mockup
        result = mvc.perform(get("/api/layers/foo?page=0&count=10&sort=name:asc&filter=o"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        
        result = mvc.perform(get("/api/layers/foo?page=0&count=10&sort=invalidparameter:badrequest&filter=o"))
                .andExpect(status().isBadRequest())
                .andReturn();

    }

    @SuppressWarnings("unused")
    @Test
    public void testGet() throws Exception {
        GeoServer gs = MockGeoServer.get().catalog()
            .workspace("foo", "http://scratch.org", true)
                .layer("one")
                    .info("The layer", "This layer is cool!")
                    .style().point().layer()
                .featureType().defaults().store("foo")
            .geoServer().build(geoServer);

        LayerInfo l = gs.getCatalog().getLayerByName("foo:one");
        l.getMetadata().put("timeout", 1000);
        gs.getCatalog().save(l);
        MvcResult result = mvc.perform(get("/api/layers/foo/one"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        JSONObj obj = JSONWrapper.read(result.getResponse().getContentAsString()).toObject();

        assertEquals("one", obj.str("name"));
        assertEquals("foo", obj.str("workspace"));
        assertEquals("vector", obj.str("type"));
        assertEquals("The layer", obj.str("title"));
        assertEquals("This layer is cool!", obj.str("description"));
        assertEquals("point", obj.object("style").str("name"));

        assertEquals("EPSG:4326", obj.object("proj").str("srs"));

        assertEquals(-180d, obj.object("bbox").object("native").doub("west"), 0.1);
        assertEquals(-90d, obj.object("bbox").object("native").doub("south"), 0.1);
        assertEquals(180d, obj.object("bbox").object("native").doub("east"), 0.1);
        assertEquals(90d, obj.object("bbox").object("native").doub("north"), 0.1);
        assertEquals(0d, obj.object("bbox").object("native").array("center").doub(0), 0.1);
        assertEquals(0d, obj.object("bbox").object("native").array("center").doub(1), 0.1);

        assertEquals(-180d, obj.object("bbox").object("lonlat").doub("west"), 0.1);
        assertEquals(-90d, obj.object("bbox").object("lonlat").doub("south"), 0.1);
        assertEquals(180d, obj.object("bbox").object("lonlat").doub("east"), 0.1);
        assertEquals(90d, obj.object("bbox").object("lonlat").doub("north"), 0.1);
        assertEquals(0d, obj.object("bbox").object("lonlat").array("center").doub(0), 0.1);
        assertEquals(0d, obj.object("bbox").object("lonlat").array("center").doub(1), 0.1);
        
        assertEquals(1000L, obj.get("timeout"));

        assertNotNull(obj.get("modified"));
        assertNotNull(obj.get("created"));

        String mod = obj.object("modified").str("timestamp");
        //TODO: this fails for some configuraitons, like our build server, investigate
        //assertTrue(Pattern.compile(".*\\d{2}:\\d{2}").matcher(mod).matches());
    }

    @Test
    public void testGetStyle() throws Exception {
        MockGeoServer.get().catalog()
          .workspace("foo", "http://scratch.org", true)
            .layer("one")
              .style().point()
          .geoServer().build(geoServer);

        MvcResult result = mvc.perform(get("/api/layers/foo/one/style"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(YsldMessageConverter.MEDIA_TYPE))
            .andReturn();

        StyledLayerDescriptor sld = Ysld.parse(result.getResponse().getContentAsString());
        Style style = ((NamedLayer)sld.layers().get(0)).getStyles()[0];

        assertTrue(style.featureTypeStyles().get(0).rules().get(0).symbolizers().get(0) instanceof PointSymbolizer);
    }

    @Test
    public void testGetStyleRaw() throws Exception {
        MockGeoServer.get().catalog()
          .resources()
            .resource("workspaces/foo/styles/one.yaml", "title: raw")
          .geoServer().catalog()
            .workspace("foo", "http://scratch.org", true)
              .layer("one")
                .style().ysld("one.yaml")
          .geoServer().build(geoServer);

        MvcResult result = mvc.perform(get("/api/layers/foo/one/style"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(YsldMessageConverter.MEDIA_TYPE))
                .andReturn();

        assertEquals("title: raw", result.getResponse().getContentAsString());
    }

    @SuppressWarnings("unused")
    @Test
    public void testPutStyle() throws Exception {
        MockGeoServer.get().catalog()
            .resources()
                .resource("workspaces/foo/styles/one.yaml", "title: blah")
            .geoServer().catalog()
                .workspace("foo", "http://scratch.org", true)
                   .layer("one")
                      .style().ysld("one.yaml")
            .geoServer().build(geoServer);


        MockHttpServletRequestBuilder req = put("/api/layers/foo/one/style")
            .contentType(YsldMessageConverter.MEDIA_TYPE)
            .content("title: raw");

        MvcResult result = mvc.perform(req)
            .andExpect(status().isOk())
            .andReturn();

        Resource r = geoServer.getCatalog().getResourceLoader().get("workspaces/foo/styles/one.yaml");
        assertEquals("title: raw", toString(r));
    }

    @Test
    public void testPutStyleInvalid() throws Exception {
        MockGeoServer.get().catalog()
            .resources()
                .resource("workspaces/foo/styles/one.yaml", "title: blah")
            .geoServer().catalog()
                .workspace("foo", "http://scratch.org", true)
                    .layer("one")
                     .style().ysld("one.yaml")
            .geoServer().build(geoServer);

        MockHttpServletRequestBuilder req = put("/api/layers/foo/one/style")
            .contentType(YsldMessageConverter.MEDIA_TYPE)
            .content("title: raw\nbad");

        MvcResult result = mvc.perform(req)
            .andExpect(status().isBadRequest())
            .andReturn();

        JSONObj obj = JSONWrapper.read(result.getResponse().getContentAsString()).toObject();
        assertNotNull(obj.get("message"));
        assertNotNull(obj.get("cause"));
        assertNotNull(obj.get("trace"));

        JSONArr arr = obj.array("errors");
        assertEquals(1, arr.size());

        assertNotNull(arr.object(0).get("problem"));
        assertNotNull(arr.object(0).get("line"));
        assertNotNull(arr.object(0).get("column"));
    }

    @SuppressWarnings("unused")
    @Test
    public void testDelete() throws Exception {
        GeoServer gs = MockGeoServer.get().catalog()
            .workspace("foo", "http://scratch.org", true)
                .layer("one")
                    .info("The layer", "This layer is cool!")
                    .meta(Metadata.IMPORTED, new Date())
                    .style().layer()
                .featureType().store("foo")
                .geoServer().build(geoServer);

        mvc.perform(delete("/api/layers/foo/one"))
            .andExpect(status().isNoContent())
            .andReturn();

        Catalog cat = geoServer.getCatalog();
        verify(cat, times(1)).remove(isA(LayerInfo.class));
        verify(cat, times(1)).remove(isA(StyleInfo.class));
    }

    @Test
    public void testPut() throws Exception {
        GeoServer gs = MockGeoServer.get().catalog()
            .workspace("foo", "http://scratch.org", true)
                .layer("one").info("The layer", "This layer is cool!").style().point().layer()
                .featureType().defaults().store("one")
                .geoServer().build(geoServer);

        JSONObj obj = new JSONObj().put("title", "new title").put("proj", "EPSG:4326").put("timeout", 1000);
        MockHttpServletRequestBuilder req = put("/api/layers/foo/one")
            .contentType(MediaType.APPLICATION_JSON)
            .content(obj.toString());

        mvc.perform(req).andExpect(status().isOk()).andReturn();

        LayerInfo l = gs.getCatalog().getLayerByName("foo:one");
        verify(l, times(1)).setTitle("new title");
        assertEquals(1000L, l.getMetadata().get("timeout"));
    }

    @Test
    public void testPutNameChange() throws Exception {
        GeoServer gs = MockGeoServer.get().catalog()
            .workspace("foo", "http://scratch.org", true)
                .layer("one").info("The layer", "This layer is cool!").style().point().layer()
                .featureType().defaults().store("one")
                .geoServer().build(geoServer);

        JSONObj obj = new JSONObj().put("name", "newname");
        MockHttpServletRequestBuilder req = put("/api/layers/foo/one")
                .contentType(MediaType.APPLICATION_JSON)
                .content(obj.toString());

        mvc.perform(req).andExpect(status().isOk()).andReturn();

        LayerInfo l = gs.getCatalog().getLayerByName("foo:one");
        ResourceInfo r = l.getResource();

        verify(l, times(1)).setName("newname");
        verify(r, times(1)).setName("newname");
    }

    String toString(Resource r) {
        return new String(((ByteArrayOutputStream)r.out()).toByteArray());
    }
    
    @Test
    public void testRecentLayers() throws Exception {
        @SuppressWarnings("unused")
        GeoServer gs = MockGeoServer.get().catalog()
            .workspace("foo", "http://scratch.org", true)
                .layer("layer1", "layer1").style().point().layer()
                    .featureType().defaults().store("foo").workspace()
                .layer("layer2", "layer2").style().point().layer()
                    .featureType().defaults().store("foo").workspace()
                .layer("layer3", "layer3").style().point().layer()
                    .featureType().defaults().store("foo").workspace()
                .geoServer().build(geoServer);

        JSONObj obj;
        MockHttpServletRequestBuilder req;
        obj = new JSONObj().put("title", "new title");
        req = put("/api/layers/foo/layer3")
            .contentType(MediaType.APPLICATION_JSON)
            .content(obj.toString());
        mvc.perform(req).andExpect(status().isOk()).andReturn();
        
        obj = new JSONObj().put("title", "new title");
        req = put("/api/layers/foo/layer2")
            .contentType(MediaType.APPLICATION_JSON)
            .content(obj.toString());
        mvc.perform(req).andExpect(status().isOk()).andReturn();
        
        obj = new JSONObj().put("title", "new title");
        req = put("/api/layers/foo/layer1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(obj.toString());
        mvc.perform(req).andExpect(status().isOk()).andReturn();

        verify(recent, times(1)).add(eq(LayerInfo.class), hasId("layer3"), eq("foo"));
        verify(recent, times(1)).add(eq(LayerInfo.class), hasId("layer2"), eq("foo"));
        verify(recent, times(1)).add(eq(LayerInfo.class), hasId("layer1"), eq("foo"));
        verify(recent, times(3)).add(eq(WorkspaceInfo.class), hasName("foo"));
    }

    LayerInfo hasId(final String id) {
        return argThat(new ArgumentMatcher<LayerInfo>() {
            @Override
            public boolean matches(Object argument) {
                return id.equals(((LayerInfo)argument).getId());
            }
        });
    }

    WorkspaceInfo hasName(final String name) {
        return argThat(new ArgumentMatcher<WorkspaceInfo>() {
            @Override
            public boolean matches(Object argument) {
                return name.equals(((WorkspaceInfo)argument).getName());
            }
        });
    }

}
