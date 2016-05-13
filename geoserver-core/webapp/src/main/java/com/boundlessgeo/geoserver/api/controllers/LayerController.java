/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import static org.geoserver.catalog.Predicates.equal;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.boundlessgeo.geoserver.util.RecentObjectCache;
import com.boundlessgeo.geoserver.util.RecentObjectCache.Ref;
import com.google.common.base.Throwables;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.WordUtils;
import org.geoserver.catalog.CascadeDeleteVisitor;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.CatalogFactory;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleHandler;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.Styles;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.config.GeoServer;
import org.geoserver.importer.Importer;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Paths;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resource.Type;
import org.geoserver.ysld.YsldHandler;
import org.geotools.data.DataAccess;
import org.geotools.data.FeatureSource;
import org.geotools.feature.NameImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.styling.ResourceLocator;
import org.geotools.styling.Style;
import org.geotools.styling.StyledLayerDescriptor;
import org.geotools.util.Version;
import org.geotools.util.logging.Logging;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.operation.TransformException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import com.boundlessgeo.geoserver.api.exceptions.BadRequestException;
import com.boundlessgeo.geoserver.api.exceptions.IncompleteRequestException;
import com.boundlessgeo.geoserver.api.exceptions.InvalidYsldException;
import com.boundlessgeo.geoserver.api.exceptions.NotFoundException;
import com.boundlessgeo.geoserver.json.JSONArr;
import com.boundlessgeo.geoserver.json.JSONObj;
import com.google.common.io.ByteSource;

@Controller
@RequestMapping("/api/layers")
public class LayerController extends ApiController {

    static Logger LOG = Logging.getLogger(LayerController.class);

    Importer importer;

    @Autowired
    public LayerController(GeoServer geoServer, RecentObjectCache recentCache, Importer importer) {
        super(geoServer, recentCache);
        this.importer = importer;
    }

    /**
     * API endpoint to list layers in a workspace
     * @param wsName The workspace
     * @param page Page of the list
     * @param count Number of items per page
     * @param sort Sort order (asc or desc)
     * @param textFilter Search filter to limit results
     * @param req HTTP request
     * @return List of items for the page, encoded as a JSON array
     */
    @RequestMapping(value="/{wsName:.+}", method = RequestMethod.GET)
    public @ResponseBody
    JSONObj list(@PathVariable String wsName, 
            @RequestParam(value="page", required=false) Integer page,
            @RequestParam(value="count", required=false, defaultValue=""+DEFAULT_PAGESIZE) Integer count,
            @RequestParam(value="sort", required=false) String sort, 
            @RequestParam(value="filter", required=false) String textFilter, 
            HttpServletRequest req) {
        JSONObj obj = new JSONObj();

        Catalog cat = geoServer.getCatalog();

        if ("default".equals(wsName)) {
            WorkspaceInfo def = cat.getDefaultWorkspace();
            if (def != null) {
                wsName = def.getName();
            }
        }
        Filter filter = equal("resource.namespace.prefix", wsName);
        if (textFilter != null) {
            filter = Predicates.and(filter, Predicates.fullTextSearch(textFilter));
        }
        Integer total = cat.count(LayerInfo.class, filter);

        SortBy sortBy = parseSort(sort);

        obj.put("total", total);
        obj.put("page", page != null ? page : 0);
        obj.put("count", Math.min(total, count != null ? count : total));

        JSONArr arr = obj.putArray("layers");
        try (
            CloseableIterator<LayerInfo> it = cat.list(LayerInfo.class, filter, offset(page, count), count, sortBy);
        ) {
            while (it.hasNext()) {
                layer(arr.addObject(), it.next(), req);
            }
        }

        return obj;
    }

    /**
     * API endpoint to create a new layer
     * @param wsName The workspace to create the layer in
     * @param obj description of the layer
     * @param req HTTP request
     * @return The description of the newly created layer
     */
    @RequestMapping(value = "/{wsName:.+}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public @ResponseBody JSONObj create(@PathVariable String wsName, @RequestBody JSONObj obj, HttpServletRequest req) {
        Catalog cat = geoServer.getCatalog();

        WorkspaceInfo ws = findWorkspace(wsName, cat);

        String name = obj.str("name");
        if (name == null) {
            throw new BadRequestException("Layer object requires name");
        }
        try {
            @SuppressWarnings("unused")
            LayerInfo l = findLayer(wsName, name, cat);
            throw new BadRequestException("Layer named '" + wsName + ":" + name
                    + "' already exists");
        } catch (NotFoundException good) {
        }

        LayerInfo l = null;
        try {
            if (obj.has("layer")) {
                l = createLayerFromLayer(obj.object("layer"), ws, cat);
            } else if (obj.has("resource")) {
                l = createLayerFromResource(obj.object("resource"), ws, cat);
            } else {
                throw new BadRequestException("Layer create requires from (to create from existing layer) or resource " +
                    "(to create from store data)");
            }
        }
        catch(IOException e) {
            throw new RuntimeException("Failed to create layer: " + e.getMessage(), e);
        }
        ResourceInfo r = l.getResource();
        
        // proj specified?
        JSONObj proj = obj.object("proj");
        if (proj != null) {
            String srs = null;
            try {
                srs = IO.srs(proj);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage(), e);
            }
            r.setSRS(srs);
            try {
                new CatalogBuilder(cat).setupBounds(r);
            } catch (IOException e) {
                throw new RuntimeException("Unable to set projection on resource: " + e.getMessage(), e);
            }
        }
        // bbox specified?
        if (obj.has("bbox")){
            JSONObj bbox = obj.object("bbox");
            if (bbox.has("native")) {
                r.setNativeBoundingBox(
                    new ReferencedEnvelope(IO.bounds(bbox.object("native")), r.getCRS()));
            }
            if (bbox.has("lonlat")) {
                r.setNativeBoundingBox(
                    new ReferencedEnvelope(IO.bounds(bbox.object("lonlat")), DefaultGeographicCRS.WGS84));
            }
        }
        if (r.getSRS() == null) {
            throw new IncompleteRequestException(
                    "Resource SRS unavailable, proj required for layer " + name);
        }
        if (r.getLatLonBoundingBox() == null) {
            throw new IncompleteRequestException(
                    "Resource bounds unavailable, bbox required for layer " + name);
        }
        if (r.getLatLonBoundingBox() == null) {
            throw new IncompleteRequestException(
                    "Resource bounds unavailable, bbox required for layer " + name);
        }

        // restore name in case it was replaced by duplicate
        l.getResource().setName(name);
        l.setName(name);

        // title
        String title = obj.str("title");
        if (title == null) {
            title = WordUtils.capitalize(name);
        }

        l.getResource().setTitle(title);
        l.setTitle(title);

        // description
        String desc = obj.str("description");
        if (desc != null) {
            l.getResource().setAbstract(desc);
            l.setAbstract(desc);
        }

        // copy the style into it's own unique
        try {
            l.setDefaultStyle(copyStyle(l, ws, cat));
        } catch (IOException e) {
            throw new RuntimeException("Error copying style: " + e.getMessage(), e);
        }

        Date created = new Date();
        Metadata.created(l, created);

        cat.add(l.getDefaultStyle());
        cat.add(l.getResource());
        cat.add(l);

        Metadata.modified(ws, created);
        cat.save(ws);

        return IO.layerDetails(new JSONObj(), l, req);
    }

    LayerInfo createLayerFromLayer(JSONObj from, WorkspaceInfo ws, Catalog cat) {
        LayerInfo orig = findLayer(ws.getName(), from.str("name"), cat);
        ResourceInfo origResource = orig.getResource();

        CatalogFactory factory = cat.getFactory();

        CatalogBuilder builder = new CatalogBuilder(cat);
        builder.setWorkspace(ws);

        LayerInfo l = factory.createLayer();
        if (origResource instanceof FeatureTypeInfo){
            FeatureTypeInfo resource = (FeatureTypeInfo) origResource;
            FeatureTypeInfo data = factory.createFeatureType();
            builder.updateFeatureType(data, resource);
            l.setResource(data);
        }
        else if (origResource instanceof CoverageInfo){
            CoverageInfo resource = (CoverageInfo) origResource;
            CoverageInfo data = factory.createCoverage();
            builder.updateCoverage( data,  resource);
            l.setResource(data);
        }
        else if (origResource instanceof WMSLayerInfo){
            WMSLayerInfo resource = (WMSLayerInfo) origResource;
            WMSLayerInfo data = factory.createWMSLayer();
            builder.updateWMSLayer( data,  resource);
            l.setResource(data);
        }
        else {
            throw new BadRequestException("Unable to copy layer from " + origResource.getClass().getSimpleName());
        }

        l.setDefaultStyle(orig.getDefaultStyle());
        //builder.updateLayer( l, orig );
        return l;
    }

    LayerInfo createLayerFromResource(JSONObj ref, WorkspaceInfo ws, Catalog cat) throws IOException {
        String storeName = ref.str("store");
        Name resourceName = new NameImpl(ref.str("name"));

        CatalogBuilder builder = new CatalogBuilder(cat);

        StoreInfo store = findStore(ws.getName(), storeName, cat);

        if( store instanceof DataStoreInfo){
            DataStoreInfo dataStore = (DataStoreInfo) store;
            builder.setStore(dataStore);

            // create from the resource
            FeatureTypeInfo ft = null;
            try {
                ft = builder.buildFeatureType(resourceName);
            }
            catch(Exception e) {
                Throwables.propagateIfInstanceOf(e, IOException.class);
                Throwables.propagate(e);
            }

            DataAccess data = dataStore.getDataStore(null);

            FeatureSource source = data.getFeatureSource(resourceName);
            builder.setupBounds(ft, source);

            return builder.buildLayer(ft);
        }
        else if (store instanceof CoverageStoreInfo) {
            CoverageStoreInfo covStore = (CoverageStoreInfo) store;
            builder.setStore(covStore);

            CoverageInfo cov = null;
            try {
                cov = builder.buildCoverage(resourceName.getLocalPart());
            } catch (Exception e) {
                Throwables.propagateIfInstanceOf(e, IOException.class);
                Throwables.propagate(e);
            }

            return builder.buildLayer(cov);
        }
        else {
            throw new UnsupportedOperationException("Copy for non vector/raster layer currently unsupported");
        }
    }

    StyleInfo copyStyle(LayerInfo l, WorkspaceInfo ws, Catalog cat) throws IOException {
        StyleInfo orig = l.getDefaultStyle();
        StyleInfo dup = cat.getFactory().createStyle();

        new CatalogBuilder(cat).updateStyle(dup, orig);
        dup.setWorkspace(ws);

        // find a unique name for the style
        String name = findUniqueStyleName(l.getName(), ws, cat);
        dup.setName(name);

        // update it's file name
        dup.setFilename(name + "." + FilenameUtils.getExtension(orig.getFilename()));

        // copy over the style contents
        try (
            BufferedReader reader = cat.getResourcePool().readStyle(orig);
        ) {
            cat.getResourcePool().writeStyle(dup, new ByteArrayInputStream(IOUtils.toByteArray(reader)));
        }

        return dup;
    }

    String findUniqueStyleName(String name, WorkspaceInfo ws, Catalog cat) {
        StyleInfo style = cat.getStyleByName(ws, name);
        if (style == null) {
            return name;
        }

        String styleName = null;
        int i = 1;
        while (style != null) {
            styleName = name + i;
            style = cat.getStyleByName(ws, styleName);
            i++;
        }
        return styleName;
    }

    /**
     * API endpoint to get details on a specific layer
     * @param wsName The workspace name
     * @param name The layer name
     * @param req The HTTP request
     * @return The layer, encoded as a JSON object
     */
    @RequestMapping(value="/{wsName}/{name:.+}", method = RequestMethod.GET)
    public @ResponseBody JSONObj get(@PathVariable String wsName, @PathVariable String name, HttpServletRequest req) {
        LayerInfo l = findLayer(wsName, name, geoServer.getCatalog());
        return layer(new JSONObj(), l, req);
    }

    /**
     * API endpoint to delete a layer from the catalog
     * @param wsName The workspace name
     * @param name The layer name
     */
    @RequestMapping(value="/{wsName}/{name:.+}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String wsName, @PathVariable String name) throws IOException {
        Catalog cat = geoServer.getCatalog();
        WorkspaceInfo ws = findWorkspace(wsName, cat);
        LayerInfo layer = findLayer(wsName, name, cat);

        recent.remove(LayerInfo.class, layer);
        recent.add(WorkspaceInfo.class, ws);

        CascadeDeleteVisitor remover = new CascadeDeleteVisitor(cat);
        remover.visit(layer);

        // if this layer was imported, delete it's style as well
        if (layer.getMetadata().containsKey(Metadata.IMPORTED)) {
            StyleInfo s = layer.getDefaultStyle();
            try {
                remover.visit(s);
                dataDir().style(s).delete();
            }
            catch(Exception e) {
                LOG.log(Level.WARNING, "Unable to delete style for layer " + wsName + ":" + layer.getName(), e);
            }
        }
    }

    /**
     * API endpoint to update an existing layer
     * @param wsName The workspace of the layer
     * @param name The name of the layer
     * @param obj Partial description of the layer, containing all changes
     * @param req HTTP request
     * @return The description of the updated layer
     * @throws IOException
     * @throws FactoryException 
     * @throws TransformException 
     * @throws NoSuchAuthorityCodeException 
     */
    @RequestMapping(value="/{wsName}/{name:.+}", method = RequestMethod.PATCH)
    public @ResponseBody JSONObj patch(@PathVariable String wsName, @PathVariable String name, @RequestBody JSONObj obj, HttpServletRequest req) throws IOException, NoSuchAuthorityCodeException, TransformException, FactoryException {
        Catalog cat = geoServer.getCatalog();
        WorkspaceInfo ws = findWorkspace(wsName, cat);
        LayerInfo layer = findLayer(wsName, name, cat);
        return update(layer, ws, obj,req);
    }

    /**
     * API endpoint to update an existing layer
     * @param wsName The workspace of the layer
     * @param name The name of the layer
     * @param obj Partial description of the layer, containing all changes
     * @param req HTTP request
     * @return The description of the updated layer
     * @throws IOException
     * @throws FactoryException 
     * @throws TransformException 
     * @throws NoSuchAuthorityCodeException 
     */
    @RequestMapping(value="/{wsName}/{name:.+}", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody JSONObj put(@PathVariable String wsName, @PathVariable String name, @RequestBody JSONObj obj, HttpServletRequest req) throws IOException, NoSuchAuthorityCodeException, TransformException, FactoryException {
        Catalog cat = geoServer.getCatalog();
        WorkspaceInfo ws = findWorkspace(wsName, cat);
        LayerInfo layer = findLayer(wsName, name, cat);
        return  update(layer, ws, obj,req);
    }
    
    JSONObj update(LayerInfo layer, WorkspaceInfo ws, JSONObj obj, HttpServletRequest req) throws NoSuchAuthorityCodeException, TransformException, FactoryException, IOException {
        ResourceInfo resource = layer.getResource();
        for (String prop : obj.keys()) {
            if ("name".equals(prop)) {
                String name = obj.str("name");
                layer.setName(name);
                resource.setName(name);
            } else if ("title".equals(prop)) {
                layer.setTitle(obj.str("title"));
            } else if ("description".equals(prop)) {
                layer.setAbstract(obj.str("description"));
            } else if ("bbox".equals(prop)) {
                JSONObj bbox = obj.object("bbox");
                if (bbox.has("native")) {
                    resource.setNativeBoundingBox(
                        new ReferencedEnvelope(IO.bounds(bbox.object("native")), resource.getCRS()));
                }
                if (bbox.has("lonlat")) {
                    resource.setLatLonBoundingBox(
                        new ReferencedEnvelope(IO.bounds(bbox.object("lonlat")), DefaultGeographicCRS.WGS84));
                } else if (bbox.has("native")) {
                    //If the native bbox has changed, update the latlon bbox
                    computeLatLonBounds(resource, geoServer.getCatalog());
                }
            } else if ("proj".equals(prop)) {
                String srs = obj.str("proj");
                try {
                    CRS.decode(srs);
                } catch (Exception e) {
                    throw new BadRequestException("Unknown spatial reference identifier: " + srs);
                }
                resource.setSRS(srs);
                //If bbox is not defined, update it.
                if (obj.get("bbox") == null) {
                    computeLatLonBounds(resource, geoServer.getCatalog());
                }
            } else if ("timeout".equals(prop)){
                layer.getMetadata().put("timeout", (Serializable)obj.get("timeout"));
            }
        }

        Metadata.modified(layer, new Date());
        Catalog cat = geoServer.getCatalog();
        cat.save(resource);
        cat.save(layer);

        recent.add(LayerInfo.class, layer, ws.getName());
        recent.add(WorkspaceInfo.class, ws);

        return IO.layerDetails(new JSONObj(), layer, req);
    }
    
    public void computeNativeBounds(ResourceInfo r, Catalog catalog) throws IOException {
        //Need to save the resource for cb to work
        CatalogBuilder cb = new CatalogBuilder(catalog);
        r.setNativeBoundingBox(cb.getNativeBounds(r));
        
    }
    public void computeLatLonBounds(ResourceInfo r, Catalog catalog) throws IOException, NoSuchAuthorityCodeException, FactoryException {
        CatalogBuilder cb = new CatalogBuilder(catalog);
        if (r.getNativeBoundingBox() == null) {
            computeNativeBounds(r, catalog);
        }
        r.setLatLonBoundingBox(cb.getLatLonBounds(r.getNativeBoundingBox(), CRS.decode(r.getSRS())));
    }

    @RequestMapping(value="/{wsName}/{name}/style", method = RequestMethod.PUT, consumes = YsldHandler.MIMETYPE)
    @ResponseStatus(value = HttpStatus.OK)
    public void style(@RequestBody byte[] rawStyle, @PathVariable String wsName,
        @PathVariable String name, @RequestParam(value="map", required=false) String mapName) throws IOException {
        // first thing is sanity check on the style content
        List<MarkedYAMLException> errors = (List) Styles.handler(YsldHandler.FORMAT).validate(ByteSource.wrap(rawStyle).openStream(), null, null);

        if (!errors.isEmpty()) {
            throw new InvalidYsldException(errors);
        }

        Catalog cat = geoServer.getCatalog();
        WorkspaceInfo ws = findWorkspace(wsName, cat);
        LayerInfo l = findLayer(wsName, name, cat);

        StyleInfo s = l.getDefaultStyle();
        
        //Style does not exist, create one
        if (s == null) {
            // create one
            s = createYsld(name, ws);
            l.getStyles().add(s);
            l.setDefaultStyle(s);
        }
        //Converting from an existing style in a different format
        else if (!YsldHandler.FORMAT.equalsIgnoreCase(s.getFormat())) {
            
            // reuse base file name
            String base = FilenameUtils.getBaseName(s.getFilename());
            l.getStyles().add(s);
            
            //Create a new style and set as the default
            s = createYsld(base+"_YSLD", ws);
            l.getStyles().add(s);
            l.setDefaultStyle(s);
         }

        s.setFormat(YsldHandler.FORMAT);
        s.setFormatVersion(new Version("1.0.0"));

        // write out the resource
        OutputStream output = dataDir().style(s).out();
        try {
            try {
                IOUtils.copy(ByteSource.wrap(rawStyle).openStream(), output);
                output.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        finally {
            try {
                //For a FileSystemResource, a temp file is copied to the actual file on close().
                output.close();
            } catch (NullPointerException npe) {
                //The temp file has already been saved and deleted, therefore npe (not an error).
            } catch (IOException ioe) {
                //Failed to modify one of the files. We have probably failed to save the changes.
                throw new RuntimeException("Failed to modify resource",ioe);
            }
        }

        if (s.getId() == null) {
            cat.add(s);
        }
        else {
            cat.save(s);
        }

        Date mod = new Date();
        Metadata.modified(l, mod);

        LayerGroupInfo map = null;
        if (mapName != null) {
            map = findMap(wsName, mapName, cat);
        }

        cat.save(l);
        recent.add(LayerInfo.class, l, wsName);
        recent.add(WorkspaceInfo.class, ws);
        if (map != null) {
            Metadata.modified(map, mod);
            cat.save(map);

            recent.add(LayerGroupInfo.class, map);
        }
    }
    
    private StyleInfo createYsld(String name, WorkspaceInfo ws) {
        StyleInfo s = geoServer.getCatalog().getFactory().createStyle();
        s.setName(findUniqueStyleName(ws.getName(), name, geoServer.getCatalog()));
        s.setFilename(s.getName()+".yaml");
        s.setWorkspace(ws);
        
        return s;
    }


    @RequestMapping(value="/{wsName}/{name}/style", method = RequestMethod.GET, produces = YsldHandler.MIMETYPE)
    public @ResponseBody Object style(@PathVariable String wsName, @PathVariable String name)
        throws IOException {
        Catalog cat = geoServer.getCatalog();
        LayerInfo l = findLayer(wsName, name, cat);
        StyleInfo s = l.getDefaultStyle();
        if (s == null) {
            throw new NotFoundException(String.format("Layer %s:%s has no default style", wsName, name));
        }

        // if the style is already stored in ySLD format just pull it directly, otherwise encode the style
        if (YsldHandler.FORMAT.equalsIgnoreCase(s.getFormat())) {
            return dataDir().style(s);
        }
        else {            
            GeoServerResourceLoader rl = cat.getResourceLoader();
            String path;
            if( s.getWorkspace() == null ){
                path = Paths.path("styles",s.getFilename());
            }
            else {
                path = Paths.path("workspaces",s.getWorkspace().getName(),"styles",s.getFilename());
            }
            final Resource r = rl.get(path);
            
            // Similar to s.getStyle() and GeoServerDataDirectory.parsedStyle(s)
            // But avoid resolving external graphics to absolute file references 
            if ( r == null || r.getType() == Type.UNDEFINED ){
                throw new IOException( "No such resource: " + s.getFilename());
            }
            // Force use of unmodified URI, avoiding absolute file references
            ResourceLocator locator = new ResourceLocator(){
                public URL locateResource(String spec) {
                    return null;
                }
            };            
            StyleHandler handler = Styles.handler(s.getFormat());
            StyledLayerDescriptor sld = handler.parse(r, s.getFormatVersion(), locator, null);
            
            final Style style = Styles.style(sld); // extract 1st style
            return Styles.sld(style);              // encode in generated SLD
        }
    }
    
    @ExceptionHandler(InvalidYsldException.class)
    public @ResponseBody JSONObj error(InvalidYsldException e, HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        
        JSONObj obj = IO.error(new JSONObj(), e);
        JSONArr errors = obj.putArray("errors");
        for (MarkedYAMLException error : e.errors()) {
            JSONObj err = errors.addObject()
                .put("problem", error.getProblem());
            Mark mark = error.getProblemMark();
            if (mark != null) {
                err.put("line", mark.getLine()).put("column", mark.getColumn());
            }
        }
        return obj;
    }
    
    @RequestMapping(value="/recent", method = RequestMethod.GET)
    public @ResponseBody JSONArr recent(HttpServletRequest req) {
        Catalog cat = geoServer.getCatalog();
        JSONArr arr = new JSONArr();
        
        for (Ref ref : recent.list(LayerInfo.class)) {
            LayerInfo layer = cat.getLayer(ref.id);
            if (layer != null) {
                IO.layerDetails(arr.addObject(), layer, req);
            }
        }
        return arr;
    }

    JSONObj layer(JSONObj obj, LayerInfo l, HttpServletRequest req) {
        IO.layerDetails(obj, l, req);
        if (!obj.has("modified")) {
            Resource r = dataDir().config(l);
            if (r.getType() != Type.UNDEFINED) {
                IO.date(obj.putObject("modified"), new Date(r.lastmodified()));
            }
        }
        return obj;
    }

    String findUniqueStyleName(String wsName, String name, Catalog cat) {
        String tryName = name;
        int i = 0;
        while (i++ < 100) {
            if (cat.getStyleByName(wsName, tryName) == null) {
                return tryName;
            }
            tryName = name + String.valueOf(i);
        }
        throw new RuntimeException("Unable to find unique name for style");
    }
}
