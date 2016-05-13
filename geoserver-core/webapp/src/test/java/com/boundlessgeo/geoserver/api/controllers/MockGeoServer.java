/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import static com.boundlessgeo.geoserver.api.controllers.ApiController.DEFAULT_PAGESIZE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.xml.transform.TransformerException;

//import org.apache.wicket.util.file.Files;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerGroupInfo.Mode;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.CatalogFactoryImpl;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.catalog.util.CloseableIteratorAdapter;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Paths;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resource.Type;
import org.geoserver.platform.resource.ResourceStore;
import org.geoserver.ysld.YsldHandler;
import org.geotools.data.DataUtilities;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.NameImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.Rule;
import org.geotools.styling.SLDTransformer;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.geotools.util.KVP;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Helper to mock up GeoServer configuration.
 */
public class MockGeoServer {

    public static interface Mocker<T> {
        void mock(T mock);
    };

    CatalogBuilder catalog;

    public static MockGeoServer get() {
        return new MockGeoServer();
    }

    public MockGeoServer() {
        catalog = new CatalogBuilder(this);
    }

    public GeoServer build(GeoServer geoServer) {
        catalog.apply(geoServer);
        return geoServer;
    }

    CatalogBuilder catalog() {
        return catalog;
    }

    public abstract class Builder {

        public abstract MockGeoServer geoServer();

    }

    public class ResourcesBuilder extends  Builder {
        File base;
        Resource baseResource = mock(Resource.class);
        List<String> paths;
        ResourceStore resourceStore;
        GeoServerResourceLoader resourceLoader;
        CatalogBuilder catalogBuilder;

        public ResourcesBuilder(CatalogBuilder catalogBuilder) {
            this.catalogBuilder = catalogBuilder;
            this.paths = new ArrayList<String>();
            
            try {
                base = File.createTempFile("resource","base");
                base.deleteOnExit();
                base.mkdir();
            } catch (IOException e) {
                base = null;
            }
            this.resourceStore = mock(ResourceStore.class);
            
            when(baseResource.dir()).thenReturn(base);
            when(resourceStore.get(Paths.BASE)).thenReturn(baseResource);
            
            this.resourceLoader = new GeoServerResourceLoader(resourceStore);
            when(catalogBuilder.catalog.getResourceLoader()).thenReturn(resourceLoader);
        }

        public ResourcesBuilder resource(String path, String content) {
            return resource(path, new ByteArrayInputStream(content.getBytes()));
        }

        public ResourcesBuilder resource(String path, InputStream content) {
            Resource r = mock(Resource.class);
            when(r.path()).thenReturn(path);
            when(r.name()).thenReturn(path.substring(path.lastIndexOf('/')+1));
            when(r.getType()).thenReturn(Type.RESOURCE);
            when(r.lastmodified()).thenReturn( System.currentTimeMillis() );
            when(r.in()).thenReturn(content);
            when(r.out()).thenReturn(new ByteArrayOutputStream());
            
            when(resourceStore.get(path)).thenReturn(r);
            paths.add( path );
            return this;
        }

        public ResourcesBuilder directory(final String path) {
            Resource r = mock(Resource.class);
            when(r.path()).thenReturn(path);
            when(r.name()).thenReturn(path.substring(path.lastIndexOf('/')+1));
            when(r.getType()).thenReturn(Type.DIRECTORY);
            when(r.dir()).then( new Answer<File>() {
                @Override
                public File answer(InvocationOnMock invocation) throws Throwable {
                    File dir = new File(base,path);
                    dir.mkdirs();
                    return dir;
                }
            });
            when(resourceStore.get(path)).thenReturn(r);
            paths.add( path );
            
            when(r.list()).then( new Answer<List<Resource>>() {
                @Override
                public List<Resource> answer(InvocationOnMock invocation) throws Throwable {
                    final List<String> c = new ArrayList<String>();
                    for(String p : paths ){
                        if( p.startsWith(path+'/')){
                            String n = p.substring(path.length()+1);
                            if( n.indexOf('/')!=-1){
                                n = n.substring(0,n.indexOf('/'));
                            }
                            if( !c.contains(n)){
                                c.add(n);
                            }                            
                        }
                    }
                    List<Resource> answer = new ArrayList<Resource>();
                    for( int i=0;i<c.size();i++){
                        answer.add( resourceStore.get(Paths.path(path,c.get(i))) );
                    }
                    return answer;
                }
            });
            return this;
        }
        
        @Override
        public MockGeoServer geoServer() {
            return catalogBuilder.geoServer();
        }
    }

    public class CatalogBuilder extends Builder {

        Catalog catalog;

        MockGeoServer geoServer;
        List<WorkspaceBuilder> workspaces = new ArrayList<WorkspaceBuilder>();

        public CatalogBuilder(MockGeoServer geoServer) {
            this.geoServer = geoServer;
            this.catalog = mock(Catalog.class);
            when(catalog.getFactory()).thenReturn(new CatalogFactoryImpl(catalog));
        }

        public WorkspaceBuilder workspace(String name, String uri, boolean isDefault) {
            WorkspaceBuilder wsBuilder = new WorkspaceBuilder(name, uri, isDefault, this);
            workspaces.add(wsBuilder);
            return wsBuilder;
        }

        public ResourcesBuilder resources() {
            return new ResourcesBuilder(this);
        }

        public MockGeoServer geoServer() {
            return geoServer;
        }

        private void apply(GeoServer geoServer) {
            // data structures
            List<WorkspaceInfo> allWorkspaces = Lists.transform(workspaces,
                    new Function<WorkspaceBuilder, WorkspaceInfo>() {
                        @Nullable
                        @Override
                        public WorkspaceInfo apply(@Nullable WorkspaceBuilder builder) {
                            return builder.workspace;
                        }
                    });
            List<NamespaceInfo> allNamespaces = Lists.transform(workspaces,
                    new Function<WorkspaceBuilder, NamespaceInfo>() {
                        @Nullable
                        @Override
                        public NamespaceInfo apply(@Nullable WorkspaceBuilder builder) {
                            return builder.namespace;
                        }
                    });
            final List<LayerInfo> allLayers = new ArrayList<LayerInfo>();
            final List<LayerGroupInfo> allMaps = new ArrayList<LayerGroupInfo>();

            when(catalog.getWorkspaces()).thenReturn(allWorkspaces);
            when(catalog.list(WorkspaceInfo.class, Predicates.acceptAll()))
                .thenReturn(new CloseableIteratorAdapter<WorkspaceInfo>(allWorkspaces.iterator()));
            when(catalog.getNamespaces()).thenReturn(allNamespaces);
            when(catalog.list(NamespaceInfo.class, Predicates.acceptAll()))
                .thenReturn(new CloseableIteratorAdapter<NamespaceInfo>(allNamespaces.iterator()));


            for (WorkspaceBuilder wsBuilder : workspaces) {
                final List<LayerInfo> layers = Lists.transform(wsBuilder.layers, new Function<LayerBuilder, LayerInfo>() {
                    @Nullable
                    @Override
                    public LayerInfo apply(@Nullable LayerBuilder layerBuilder) {
                        return layerBuilder.layer;
                    }
                });
                allLayers.addAll(layers);
                for(LayerBuilder layer : wsBuilder.layers ){
                    String layerName = wsBuilder.namespace.getPrefix()+":"+layer.name;
                    when(catalog.getLayerByName( layerName )).thenReturn(layer.layer);
                }

                Answer<CloseableIteratorAdapter<LayerInfo>> a = new Answer<CloseableIteratorAdapter<LayerInfo>>() {
                    @Override
                    public CloseableIteratorAdapter<LayerInfo> answer(InvocationOnMock invocation) throws Throwable {
                        return new CloseableIteratorAdapter<LayerInfo>(layers.iterator());
                    }
                };
                when(catalog.list(LayerInfo.class, Predicates.equal("resource.namespace.prefix",
                    wsBuilder.workspace.getName()))).thenAnswer(a);
                when(catalog.list(LayerInfo.class, Predicates.equal("resource.namespace.prefix",
                    wsBuilder.workspace.getName()), null, DEFAULT_PAGESIZE, null)).thenAnswer(a);
                when(catalog.count(LayerInfo.class, Predicates.equal("resource.namespace.prefix",
                    wsBuilder.workspace.getName()))).thenReturn(wsBuilder.layers.size());
                
                final List<LayerGroupInfo> maps = Lists.transform(wsBuilder.maps, new Function<MapBuilder, LayerGroupInfo>() {
                    @Nullable
                    @Override
                    public LayerGroupInfo apply(@Nullable MapBuilder mapBuilder) {
                        return mapBuilder.map;
                    }
                });
                allMaps.addAll(maps);

                Answer<CloseableIteratorAdapter<LayerGroupInfo>> b = new Answer<CloseableIteratorAdapter<LayerGroupInfo>>() {
                    @Override
                    public CloseableIteratorAdapter<LayerGroupInfo> answer(InvocationOnMock invocation) throws Throwable {
                        return new CloseableIteratorAdapter<LayerGroupInfo>(maps.iterator());
                    }
                };
                when(catalog.list(LayerGroupInfo.class, Predicates.equal("workspace.name",
                    wsBuilder.workspace.getName()))).thenAnswer(b);
                when(catalog.list(LayerGroupInfo.class, Predicates.equal("workspace.name",
                    wsBuilder.workspace.getName()), null, DEFAULT_PAGESIZE, null)).thenAnswer(b);
                when(catalog.list(LayerGroupInfo.class, Predicates.equal("workspace.name",
                    wsBuilder.workspace.getName()))).thenAnswer(b);
                when(catalog.count(LayerGroupInfo.class, Predicates.equal("workspace.name",
                        wsBuilder.workspace.getName()))).thenReturn(wsBuilder.maps.size());
            }
            when(catalog.getLayers()).thenReturn(allLayers);
            when(catalog.list(LayerInfo.class, Predicates.acceptAll())).thenReturn(
                new CloseableIteratorAdapter<LayerInfo>(allLayers.iterator()));
            when(catalog.list(LayerInfo.class, Predicates.acceptAll())).thenReturn(
                    new CloseableIteratorAdapter<LayerInfo>(allLayers.iterator()));
            // layer group
            when(catalog.getLayerGroups()).thenReturn(allMaps);
            when(catalog.list(LayerGroupInfo.class, Predicates.acceptAll())).thenReturn(
                    new CloseableIteratorAdapter<LayerGroupInfo>(allMaps.iterator()));
            
            // Handle arbitrary filtered listings of layers
            final ArgumentCaptor<Filter> layerPredicate = ArgumentCaptor.forClass(Filter.class);
            Answer<CloseableIterator<LayerInfo>> layerFilteredAnswer = new Answer<CloseableIterator<LayerInfo>>() {
                
                @Override
                public CloseableIterator<LayerInfo> answer(
                        InvocationOnMock invocation) throws Throwable {
                    Filter f = layerPredicate.getValue();
                    return CloseableIteratorAdapter.filter(allLayers.iterator(),f);
                }
                
            };
            when(catalog.list(eq(LayerInfo.class), layerPredicate.capture())).thenAnswer(layerFilteredAnswer);

            // Handle arbitrary filtered listings of layer groups
            final ArgumentCaptor<Filter> layerGroupPredicate = ArgumentCaptor.forClass(Filter.class);
            Answer<CloseableIterator<LayerGroupInfo>> layerGroupFilteredAnswer = new Answer<CloseableIterator<LayerGroupInfo>>() {
                
                @Override
                public CloseableIterator<LayerGroupInfo> answer(
                        InvocationOnMock invocation) throws Throwable {
                    Filter f = layerGroupPredicate.getValue();
                    return CloseableIteratorAdapter.filter(allMaps.iterator(),f);
                }
                
            };
            when(catalog.list(eq(LayerGroupInfo.class), layerGroupPredicate.capture())).thenAnswer(layerGroupFilteredAnswer);
            
            when(geoServer.getCatalog()).thenReturn(catalog);
        }
    }

    public class WorkspaceBuilder extends Builder {

        CatalogBuilder catalogBuilder;
        WorkspaceInfo workspace;
        NamespaceInfo namespace;

        List<StoreBuilder> stores = new ArrayList<StoreBuilder>();
        List<LayerBuilder> layers = new ArrayList<LayerBuilder>();
        List<MapBuilder> maps = new ArrayList<MapBuilder>();

        public WorkspaceBuilder(String name, String uri, boolean isDefault, CatalogBuilder catalogBuilder) {
            this.catalogBuilder = catalogBuilder;
            Catalog cat = catalogBuilder.catalog;

            workspace = mock(WorkspaceInfo.class);
            when(workspace.getName()).thenReturn(name);

            namespace = mock(NamespaceInfo.class);
            when(namespace.getName()).thenReturn(name);
            when(namespace.getPrefix()).thenReturn(name);
            when(namespace.getURI()).thenReturn(uri);

            when(cat.getWorkspaceByName(name)).thenReturn(workspace);
            when(cat.getNamespaceByPrefix(name)).thenReturn(namespace);
            when(cat.getNamespaceByURI(uri)).thenReturn(namespace);

            if (isDefault) {
                when(cat.getDefaultWorkspace()).thenReturn(workspace);
                when(cat.getDefaultNamespace()).thenReturn(namespace);
            }
        }

        public LayerBuilder layer(String name) {
            return layer(name, null);
        }

        public LayerBuilder layer(String name, String id) {
            LayerBuilder lBuilder = new LayerBuilder(name, id, this);
            layers.add(lBuilder);
            return lBuilder;
        }

        public MapBuilder map(String name) {
            return map(name, null);
        }

        public MapBuilder map(String name, String id) {
            MapBuilder mapBuilder = new MapBuilder(name, id, this);
            maps.add(mapBuilder);
            return mapBuilder;
        }
        
        public StoreBuilder vector(String name) {
            StoreBuilder storeBuilder = new StoreBuilder(name, this,IO.Type.VECTOR);
            stores.add(storeBuilder);
            return storeBuilder;
        }
        public StoreBuilder wms(String name) {
            StoreBuilder storeBuilder = new StoreBuilder(name, this,IO.Type.SERVICE);
            stores.add(storeBuilder);
            return storeBuilder;
        }
        public StoreBuilder raster(String name) {
            StoreBuilder storeBuilder = new StoreBuilder(name, this,IO.Type.RASTER);
            stores.add(storeBuilder);
            return storeBuilder;
        }

        public CatalogBuilder catalog() {
            return catalogBuilder;
        }

        public MockGeoServer geoServer() {
            return catalogBuilder.geoServer();
        }
        
        StoreBuilder findStore(String name ){
            for( StoreBuilder store : this.stores ){
                if( name.equals( store.name )){
                    return store;
                }
            }
            return null;
        }

    }

    public class StoreBuilder extends Builder {
        String name;
        KVP kvp;
        WorkspaceBuilder workspaceBuilder;
        public String source;
        
        StoreInfo store;
        
        
        public StoreBuilder(String name, WorkspaceBuilder workspaceBuilder, IO.Type kind) {
            this.name = name;
            this.workspaceBuilder = workspaceBuilder;

            switch (kind) {
            case VECTOR:
                store = mock(DataStoreInfo.class);
                break;
            case RASTER:
                store = mock(CoverageStoreInfo.class);
                break;
            case SERVICE:
                store = mock(WMSStoreInfo.class);
                break;
            case RESOURCE:
                store = mock(StoreInfo.class);
                break;
            }
            when(store.getName()).thenReturn(name);
        }
        
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public StoreBuilder source(String source){
            if( source.endsWith(".shp")){
                when(store.getConnectionParameters()).thenReturn(
                    (Map<String,Serializable>) (Map) new KVP("url", source) );
            }
            else if( source.endsWith(".png")||source.endsWith(".tif")||source.endsWith(".tiff")){
                when(store.getConnectionParameters()).thenReturn(
                    (Map<String,Serializable>) (Map) new KVP("raster", source) );
            }
            else if( source.startsWith("postgis")){
                String host = source.substring(8);
                when(store.getConnectionParameters()).thenReturn(
                    (Map<String,Serializable>) (Map) new KVP("dbtype", "postgis","host",host,"port","5432") );
            }
            else if( source.contains("service=wms")){
                when(store.getConnectionParameters()).thenReturn(
                    (Map<String,Serializable>) (Map) new KVP("wms", source) );
            }
            else {
                when(store.getConnectionParameters()).thenReturn(
                    (Map<String,Serializable>) (Map) new KVP("directory", source) );
            }
            return this;
        }

        public MockGeoServer geoServer() {
            return workspaceBuilder.geoServer();
        }

        public WorkspaceBuilder workspace() {
            return workspaceBuilder;
        }
    }

    public class MapBuilder extends Builder {

        String name;
        LayerGroupInfo map;
        WorkspaceBuilder workspaceBuilder;
        List<LayerBuilder> layers = new ArrayList<LayerBuilder>();

        public MapBuilder(String name, String id, WorkspaceBuilder workspaceBuilder) {
            this.name = name;
            this.workspaceBuilder = workspaceBuilder;

            id = id != null ? id : UUID.randomUUID().toString();

            String wsName = workspaceBuilder.workspace.getName();
            map = mock(LayerGroupInfo.class);

            when(map.getId()).thenReturn(id);
            when(map.getName()).thenReturn(name);
            when(map.getMode()).thenReturn(Mode.SINGLE);
            when(map.prefixedName()).thenReturn(wsName + ":" + name);
            when(map.getWorkspace()).thenReturn(workspaceBuilder.workspace);
            
            when(map.layers()).thenAnswer(new Answer<List<LayerInfo>>() {
                @Override
                public List<LayerInfo> answer(InvocationOnMock invocation) throws Throwable {
                    return new ArrayList<LayerInfo>(
                            Lists.transform(layers, new Function<LayerBuilder, LayerInfo>() {
                        @Nullable
                        @Override
                        public LayerInfo apply(@Nullable LayerBuilder input) {
                            return input.layer;
                        }
                    }));
                }
            });

            when(map.getLayers()).thenAnswer(new Answer<List<PublishedInfo>>() {
                @Override
                public List<PublishedInfo> answer(InvocationOnMock invocation) throws Throwable {
                    return new ArrayList<PublishedInfo>(
                            Lists.transform(layers, new Function<LayerBuilder, PublishedInfo>() {
                        @Nullable
                        @Override
                        public LayerInfo apply(@Nullable LayerBuilder input) {
                            return input.layer;
                        }
                    }));
                }
            });
            
            when(map.getStyles()).thenAnswer(new Answer<List<StyleInfo>>() {
                @Override
                public List<StyleInfo> answer(InvocationOnMock invocation) throws Throwable {
                    return new ArrayList<StyleInfo>(
                            Lists.transform(layers, new Function<LayerBuilder, StyleInfo>() {
                                @Nullable @Override
                                public StyleInfo apply(@Nullable LayerBuilder input) {
                                    return input.layer.getDefaultStyle();
                                }
                            }
                    ));
                }
            });

            MetadataMap meta = new MetadataMap();
            meta.put(Metadata.CREATED, new Date());
            meta.put(Metadata.MODIFIED, new Date());
            when(map.getMetadata()).thenReturn(meta);

            Catalog catalog = workspaceBuilder.catalogBuilder.catalog;
            when(catalog.getLayerGroup(id)).thenReturn(map);
            when(catalog.getLayerGroupByName(name)).thenReturn(map);
            when(catalog.getLayerGroupByName(wsName, name)).thenReturn(map);
        }

        public MapBuilder info(String title, String description) {
            when(map.getTitle()).thenReturn(title);
            when(map.getAbstract()).thenReturn(description);
            return this;
        }

        public MapBuilder bbox(double x1, double y1, double x2, double y2, CoordinateReferenceSystem crs) {
            when(map.getBounds()).thenReturn(new ReferencedEnvelope(x1,x2,y1,y2,crs));
            return this;
        }

        public MapBuilder defaults() {
            return bbox(-180,-90,180,90,DefaultGeographicCRS.WGS84);
        }

        public MapBuilder with(Mocker<LayerGroupInfo> m) {
            m.mock(map);
            return this;
        }

        public LayerBuilder layer(String name) {
            return layer(name, null);
        }
        public LayerBuilder layer(String name, String id) {
            LayerBuilder layerBuilder = new LayerBuilder(name, id, this);
            layers.add(layerBuilder);
            return layerBuilder;
        }

        @Override
        public MockGeoServer geoServer() {
            return workspaceBuilder.geoServer();
        }
        public WorkspaceBuilder workspace() {
            return workspaceBuilder;
        }
    }

    public class LayerBuilder extends Builder {

        String name;
        LayerInfo layer;

        WorkspaceBuilder workspaceBuilder;
        MapBuilder mapBuilder;
        ResourceBuilder<?,?> resourceBuilder;

        public LayerBuilder(String name, String id, WorkspaceBuilder workspaceBuilder) {
            this.name = name;
            this.workspaceBuilder = workspaceBuilder;
            String wsName = workspaceBuilder.workspace.getName();
            layer = mock(LayerInfo.class);

            id = id != null ? id : UUID.randomUUID().toString();
            when(layer.getId()).thenReturn(id);
            when(layer.getName()).thenReturn(name);
            when(layer.prefixedName()).thenReturn(wsName+":"+name);

            MetadataMap meta = new MetadataMap();
            meta.put(Metadata.CREATED, new Date());
            meta.put(Metadata.MODIFIED, new Date());
            when(layer.getMetadata()).thenReturn(meta);

            Catalog catalog = workspaceBuilder.catalogBuilder.catalog;
            
            when(catalog.getLayer(id)).thenReturn(layer);
            when(catalog.getLayerByName(wsName+":"+name)).thenReturn(layer);
            when(catalog.getLayerByName(new NameImpl(workspaceBuilder.namespace.getURI(), name))).thenReturn(layer);
        }

        public LayerBuilder(String name, String id, MapBuilder mapBuilder) {
            this(name, null, mapBuilder.workspaceBuilder);
            this.mapBuilder = mapBuilder;
        }

        public LayerBuilder info(String title, String description) {
            when(layer.getTitle()).thenReturn(title);
            when(layer.getAbstract()).thenReturn(description);
            return this;
        }

        public LayerBuilder meta(String key, Serializable val) {
            layer.getMetadata().put(key, val);
            return this;
        }

        public MapBuilder map() {
            return mapBuilder;
        }

        public StyleBuilder style() {
            return new StyleBuilder(this);
        }

        public FeatureTypeBuilder featureType() {
            return new FeatureTypeBuilder(this);
        }

        public CoverageBuilder coverage() {
            return new CoverageBuilder(this);
        }

        public WMSLayerBuilder wmsLayer() {
            return new WMSLayerBuilder(this);
        }

        public MockGeoServer geoServer() {
            return workspaceBuilder.geoServer();
        }

        public ResourceBuilder<?, ?> resource() {
            return this.resourceBuilder;
        }

        public WorkspaceBuilder workspace() {
            return this.workspaceBuilder;
        }
    }

    public abstract class  ResourceBuilder<T extends ResourceInfo, B extends ResourceBuilder<?,?>> extends Builder {
        
        protected T resource;
        protected LayerBuilder layerBuilder;

        protected ResourceBuilder(T resource, LayerBuilder layerBuilder) {
            this.resource = resource;
            this.layerBuilder = layerBuilder;

            LayerInfo layer = layerBuilder.layer;
            when(resource.getName()).thenReturn(layerBuilder.name);
            when(resource.getNativeName()).thenReturn(layerBuilder.name);
            when(resource.getNamespace()).thenAnswer(new Answer<NamespaceInfo>() {
                @Override
                public NamespaceInfo answer(InvocationOnMock invocation) throws Throwable {
                    return ResourceBuilder.this.layerBuilder.workspaceBuilder.namespace;
                }
            });
            when(layer.getResource()).thenReturn(resource);
        }
        
        public abstract B defaults();
        
        public abstract B store(final String storeName);
        
        @SuppressWarnings("unchecked")
        public B proj(String srs, CoordinateReferenceSystem crs) {
            when(resource.getSRS()).thenReturn(srs);
            when(resource.getCRS()).thenReturn(crs);
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B bbox(double x1, double y1, double x2, double y2, CoordinateReferenceSystem crs) {
            when(resource.getNativeBoundingBox()).thenReturn(new ReferencedEnvelope(x1,x2,y1,y2,crs));
            when(resource.getNativeCRS()).thenReturn(crs);
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B latLonBbox(double x1, double y1, double x2, double y2) {
            when(resource.getLatLonBoundingBox()).thenReturn(new ReferencedEnvelope(x1,x2,y1,y2, DefaultGeographicCRS.WGS84));
            return (B) this;
        }

        public WorkspaceBuilder workspace() {
            return layerBuilder.workspaceBuilder;
        }

        public LayerBuilder layer() {
            return layerBuilder;
        }
        
        public MapBuilder map() {
            return layerBuilder.map();
        }

        public MockGeoServer geoServer() {
            return workspace().catalog().geoServer();
        }
    }

    public class WMSLayerBuilder extends ResourceBuilder<WMSLayerInfo, WMSLayerBuilder> {

        public WMSLayerBuilder(LayerBuilder layerBuilder) {
            super(mock(WMSLayerInfo.class), layerBuilder);
        }

        public WMSLayerBuilder defaults() {
            proj("EPSG:4326", DefaultGeographicCRS.WGS84).bbox(-180, -90, 180, 90,
                    DefaultGeographicCRS.WGS84).latLonBbox(-180, -90, 180, 90);
            return this;
        }

        public WMSLayerBuilder store(final String storeName) {
            WorkspaceBuilder workspaceBuilder = layerBuilder.workspaceBuilder;
            if (workspaceBuilder.findStore(storeName) == null) {
                workspaceBuilder.wms(storeName);
            }
            when(resource.getStore()).thenAnswer(new Answer<StoreInfo>() {
                @Override
                public StoreInfo answer(InvocationOnMock invocation) throws Throwable {
                    return WMSLayerBuilder.this.layerBuilder.workspaceBuilder
                            .findStore(storeName).store;
                }
            });
            return this;
        }
    }
    
    public class CoverageBuilder extends ResourceBuilder<CoverageInfo, CoverageBuilder> {

        public CoverageBuilder(LayerBuilder layerBuilder) {
            super(mock(CoverageInfo.class), layerBuilder);
        }

        public CoverageBuilder defaults() {
            proj("EPSG:4326", DefaultGeographicCRS.WGS84).bbox(-180, -90, 180, 90,
                    DefaultGeographicCRS.WGS84).latLonBbox(-180, -90, 180, 90);
            return this;
        }

        public CoverageBuilder store(final String storeName) {
            WorkspaceBuilder workspaceBuilder = layerBuilder.workspaceBuilder;
            if (workspaceBuilder.findStore(storeName) == null) {
                workspaceBuilder.raster(storeName);
            }
            when(resource.getStore()).thenAnswer(new Answer<StoreInfo>() {
                @Override
                public StoreInfo answer(InvocationOnMock invocation) throws Throwable {
                    return CoverageBuilder.this.layerBuilder.workspaceBuilder
                            .findStore(storeName).store;
                }
            });
            return this;
        }
    }

    public class FeatureTypeBuilder extends ResourceBuilder<FeatureTypeInfo, FeatureTypeBuilder> {

        public FeatureTypeBuilder(LayerBuilder layerBuilder) {
            super(mock(FeatureTypeInfo.class), layerBuilder);
        }

        public FeatureTypeBuilder schema(String spec) {
            try {
                when(resource.getFeatureType()).thenReturn(DataUtilities.createType(layerBuilder.name, spec));
                return this;
            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        public FeatureTypeBuilder defaults() {
            proj("EPSG:4326", DefaultGeographicCRS.WGS84)
              .bbox(-180, -90, 180, 90, DefaultGeographicCRS.WGS84)
              .latLonBbox(-180, -90, 180, 90);

            return schema("geom:Point,name:String");
        }
        
        public FeatureTypeBuilder store(final String storeName){
            WorkspaceBuilder workspaceBuilder = layerBuilder.workspaceBuilder;
            if( workspaceBuilder.findStore(storeName) == null ){
                workspaceBuilder.vector(storeName);
            }
            when(resource.getStore()).thenAnswer( new Answer<StoreInfo>() {
                @Override
                public StoreInfo answer(InvocationOnMock invocation) throws Throwable {
                    return FeatureTypeBuilder.this.layerBuilder.workspaceBuilder.findStore(storeName).store;
                }
            });
            return this;
        }
    }

    public class StyleBuilder extends Builder {

        StyleInfo style;
        LayerBuilder layerBuilder;
        StyleFactory styleFactory;

        public StyleBuilder(LayerBuilder layerBuilder) {
            this.layerBuilder = layerBuilder;
            this.styleFactory = CommonFactoryFinder.getStyleFactory();

            style = mock(StyleInfo.class);
            when(style.getWorkspace()).thenReturn(layerBuilder.workspaceBuilder.workspace);
            when(layerBuilder.layer.getDefaultStyle()).thenReturn(style);
        }

        public StyleBuilder ysld(String filename) {
            when(style.getFormat()).thenReturn(YsldHandler.FORMAT);
            when(style.getFilename()).thenReturn(filename);
            when(style.getName()).thenReturn(filename);
            return this;
        }

        public StyleBuilder point() {
            Rule rule = styleFactory.createRule();
            rule.symbolizers().add(styleFactory.createPointSymbolizer());

            FeatureTypeStyle featureTypeStyle = styleFactory.createFeatureTypeStyle();
            featureTypeStyle.rules().add(rule);

            Style style = styleFactory.createStyle();
            style.featureTypeStyles().add(featureTypeStyle);

            try {
                when(this.style.getStyle()).thenReturn(style);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            ResourcesBuilder resources = geoServer().catalog().resources();
            String wsName = layerBuilder.workspaceBuilder.namespace.getName();
            String fileName = "point.sld";
            String name = "point";
            when(this.style.getName() ).thenReturn(name);
            when(this.style.getFilename() ).thenReturn(fileName );
            when(this.style.getWorkspace() ).thenReturn( layerBuilder.workspaceBuilder.workspace );
            when(this.style.getFormat()).thenReturn("sld");
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            SLDTransformer tx = new SLDTransformer();
            tx.setIndentation(2);
            try {
                tx.transform( style, content );
                resources.resource(Paths.path("workspaces",wsName,"styles",fileName), content.toString() );
            } catch (TransformerException e) {
                throw new RuntimeException(e);
            }
            return this;
        }

        public LayerBuilder layer() {
            return layerBuilder;
        }

        public MockGeoServer geoServer() {
            return layerBuilder.geoServer();
        }
    }
}
