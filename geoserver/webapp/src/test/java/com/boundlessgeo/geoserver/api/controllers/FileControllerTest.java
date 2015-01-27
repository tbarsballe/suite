package com.boundlessgeo.geoserver.api.controllers;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;

import org.geoserver.catalog.SLDHandler;
import org.geoserver.catalog.StyleHandler;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.ysld.YsldHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
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
import com.boundlessgeo.geoserver.util.RecentObjectCache;

public class FileControllerTest {
    
    @Mock
    GeoServer geoServer;
    
    @Mock
    RecentObjectCache recent;
    
    @InjectMocks
    FileController ctrl;
    
    MockMvc mvc;
    
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    
    public File dataDir;
    /* Temporary data dir:
     * 
     * data_dir/
     *   ws/
     *     wsfile1.xml
     *     wsfile2.txt
     *     ws1/
     *       ws1file.xml
     *       ws1s1/
     *         s1file1.shp
     *     ws2/
     *       ws2file.xml
     */
    @Before
    public void setUpDir() throws Exception {
        dataDir = folder.newFolder("data_dir");
        File ws = (new File(dataDir, "workspaces"));
        ws.mkdir();
        (new File(ws, "wsfile1.xml")).createNewFile();
        (new File(ws, "wsfile2.txt")).createNewFile();
        
        File ws1 = (new File(ws, "ws1"));
        ws1.mkdir();
        (new File(ws1, "ws1file.xml")).createNewFile();
        File ws1s1 = (new File(ws1, "s1"));
        ws1s1.mkdir();
        (new File(ws1s1, "s1file1.shp")).createNewFile();
        
        File ws2 = (new File(ws, "ws2"));
        ws2.mkdir();
        (new File(ws2, "ws2file.xml")).createNewFile();
    }
    
    @Before
    public void setUpAppContext() {
        WebApplicationContext appContext = mock(WebApplicationContext.class);
        //when(appContext.getBeanNamesForType(StyleHandler.class)).thenReturn(new String[]{"ysldHandler","sldHandler"});
        //when(appContext.getBean("ysldHandler")).thenReturn(new YsldHandler());
        //when(appContext.getBean("sldHandler")).thenReturn(new SLDHandler());
    
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
    public void testRoot() throws Exception {
        GeoServer gs = MockGeoServer.get().catalog().resources().geoServer().build(geoServer);

        MvcResult result = mvc.perform(get("/api/files/roots"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        JSONArr arr = JSONWrapper.read(result.getResponse().getContentAsString()).toArray();
        
        boolean containsDataDir = false;
        boolean containsHomeDir = false;
        for (JSONObj obj : arr.objects()) {
            assertNotNull(obj.get("name"));
            assertNotNull(obj.get("path"));
            File file = new File(obj.get("path").toString());
            assertTrue(file.exists());
            if ("Data Directory".equals(obj.get("name"))) {
                containsDataDir = true;
            }
            if ("Home Directory".equals(obj.get("name"))) {
                containsHomeDir = true;
            }
        }
        assertTrue(containsDataDir);
        assertTrue(containsHomeDir);
    }
    
    @Test
    public void testGet() throws Exception {
        GeoServer gs = MockGeoServer.get().catalog().resources().geoServer().build(geoServer);
        
        MvcResult result = mvc.perform(get("/api/files?file="+dataDir.getPath()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        JSONObj obj = JSONWrapper.read(result.getResponse().getContentAsString()).toObject();
        
        //assertEquals("data_dir", obj.str("name"));
    }
    
    @Test
    public void testGetDepth() throws Exception {
        GeoServer gs = MockGeoServer.get().catalog().resources().geoServer().build(geoServer);
        
        MvcResult result = mvc.perform(get("/api/files?file="+dataDir.getPath()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        JSONObj obj = JSONWrapper.read(result.getResponse().getContentAsString()).toObject();
        
        assertEquals(FileController.DEFAULT_FILE_DEPTH, depth(obj));
        
        result = mvc.perform(get("/api/files?file="+dataDir.getPath()+"&depth=0"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        obj = JSONWrapper.read(result.getResponse().getContentAsString()).toObject();
        
        assertEquals(0, depth(obj));
        
        result = mvc.perform(get("/api/files?file="+dataDir.getPath()+"&depth=-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        obj = JSONWrapper.read(result.getResponse().getContentAsString()).toObject();
        
        assertEquals(4, depth(obj));
        
    }
    
    private int depth(JSONObj obj) {
        JSONArr children = obj.array("children");
        //no children = depth of 0;
        int depth = -1;
        if (children != null) {
            for (JSONObj child : children.objects()) {
                depth = Math.max(depth, depth(child));
            }
        }
        return depth+1;
    }
    
    
    @Test
    public void testGetFilter() throws Exception {
        GeoServer gs = MockGeoServer.get().catalog().resources().geoServer().build(geoServer);
        
        //all files
        MvcResult result = mvc.perform(get("/api/files?file="+dataDir.getPath()+"&depth=-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        JSONObj obj = JSONWrapper.read(result.getResponse().getContentAsString()).toObject();
        assertEquals(5, countFiles(obj));
        
        //all shapefiles
        result = mvc.perform(get("/api/files?file="+dataDir.getPath()+"&depth=-1&filter=shp"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        
        obj = JSONWrapper.read(result.getResponse().getContentAsString()).toObject();
        assertEquals(1, countFiles(obj));
        
        //txt and xml files
        result = mvc.perform(get("/api/files?file="+dataDir.getPath()+"&depth=-1&filter=txt,xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        
        obj = JSONWrapper.read(result.getResponse().getContentAsString()).toObject();
        assertEquals(4, countFiles(obj));
        
        //shapefiles with depth <= 3
        result = mvc.perform(get("/api/files?file="+dataDir.getPath()+"&depth=3&filter=shp"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        obj = JSONWrapper.read(result.getResponse().getContentAsString()).toObject();
        assertEquals(0, countFiles(obj));
        
    }
    
    private int countFiles(JSONObj obj) {
        if ("file".equals(obj.get("type"))) {
            return 1;
        }
        JSONArr children = obj.array("children");
        int count = 0;
        if (children != null) {
            for (JSONObj child : children.objects()) {
                count += countFiles(child);
            }
        }
        return count;
    }
    @Test
    public void testGetInvalid() throws Exception {
        GeoServer gs = MockGeoServer.get().catalog().resources().geoServer().build(geoServer);
        
        //invalid file
        MvcResult result = mvc.perform(get("/api/files?file=invalidpath"))
                .andExpect(status().isBadRequest())
                .andReturn();
    }
    
    @Test
    public void testRecent() {
        
    }

}
