/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.api.controllers;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import com.boundlessgeo.geoserver.util.RecentObjectCache.Ref;

import org.apache.commons.httpclient.util.DateUtil;
import org.apache.commons.lang.WordUtils;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.ows.URLMangler.URLType;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.wms.WMSInfo;
import org.geotools.data.DataAccessFactory;
import org.geotools.data.DataAccessFactory.Param;
import org.geotools.data.Parameter;
import org.geotools.feature.FeatureTypes;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.filter.visitor.DuplicatingFilterVisitor;
import org.geotools.geometry.jts.Geometries;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.resources.coverage.FeatureUtilities;
import org.geotools.util.logging.Logging;
import org.ocpsoft.pretty.time.PrettyTime;
import org.opengis.coverage.grid.Format;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AssociationDescriptor;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.feature.type.PropertyType;
import org.opengis.filter.Filter;
import org.opengis.filter.expression.PropertyName;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.crs.ProjectedCRS;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.GenericName;

import com.boundlessgeo.geoserver.Proj;
import com.boundlessgeo.geoserver.json.JSONArr;
import com.boundlessgeo.geoserver.json.JSONObj;
import com.google.common.base.Throwables;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Helper for encoding/decoding objects to/from JSON.
 */
public class IO {

    static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX";

    /** Kind of provider */
    static public enum Kind {FILE,DATABASE,WEB,GENERIC;
        public String toString() {
            return name().toLowerCase();
        }
        static Kind of( ResourceInfo resource){
            return of( resource.getStore());
        }
        static Kind of( DataAccessFactory format){
            Set<String> params = new HashSet<String>();
            for (Param info : format.getParametersInfo()) {
                params.add(info.getName());
            }
            if (params.contains("dbtype")) {
                return Kind.DATABASE;
            }
            if (params.contains("directory") || params.contains("file") || params.contains("raster")) {
                return Kind.FILE;
            }
            if (params.contains("wms")
                    || params.contains("WFSDataStoreFactory:GET_CAPABILITIES_URL")) {
                return Kind.WEB;
            }
            if( params.contains("url") ){
                return Kind.FILE;
            }
            return Kind.GENERIC;
        }
        static Kind of( StoreInfo store ){
            if( store instanceof CoverageStoreInfo){
                String url = ((CoverageStoreInfo)store).getURL();
                if( url.startsWith("file")){
                    return Kind.FILE;
                }
                else if( url.startsWith("http") ||
                         url.startsWith("https") ||
                         url.startsWith("ftp") ||
                         url.startsWith("sftp")){
                    return Kind.WEB;
                }
            }
            Map<String, Serializable> params = store.getConnectionParameters();
            if(params == null ){
                return Kind.GENERIC;
            }
            else if( params.containsKey("dbtype")){
                return Kind.DATABASE;
            }
            else if( store instanceof WMSStoreInfo){
                return Kind.WEB;
            }
            else if( params.keySet().contains("directory") ||
                params.keySet().contains("file") ){
                
                return Kind.FILE;
            }
            for( Object value : params.values()){
                if( value == null ) continue;
                if( value instanceof File ||
                    (value instanceof String && ((String)value).startsWith("file:")) ||
                    (value instanceof URL && ((URL)value).getProtocol().equals("file"))){
                    return Kind.FILE;
                }
                if( (value instanceof String && ((String)value).startsWith("http:")) ||
                    (value instanceof URL && ((URL)value).getProtocol().equals("http"))){
                    return Kind.WEB;
                }
                if( value instanceof String && ((String)value).startsWith("jdbc:")){
                    return Kind.DATABASE;
                }
            }
            return Kind.GENERIC;
        }
    }
    /** Type of content: raster, vector, service(wms layer), generic resource */
    static public enum Type {RASTER,VECTOR,SERVICE,RESOURCE;
        public String toString() {
            return name().toLowerCase();
        }
        static Type of( String resource ){
            return valueOf(resource.toUpperCase());
        }
        static Type of( ResourceInfo resource ){
            if( resource instanceof CoverageInfo){
                return Type.RASTER;
            }
            else if( resource instanceof FeatureTypeInfo){
                return Type.VECTOR;
            }
            else if(resource instanceof WMSLayerInfo){
                return Type.SERVICE;
            }
            return Type.RESOURCE;
        }
        static Type of( StoreInfo store ){
            if( store instanceof CoverageStoreInfo){
                return Type.RASTER;
            }
            else if( store instanceof DataStoreInfo){
                return Type.VECTOR;
            }
            else if(store instanceof WMSStoreInfo){
                return Type.SERVICE;
            }
            return Type.RESOURCE;
        }
    }

    static Logger LOG = Logging.getLogger(IO.class);

    /**
     * Encodes a projection within the specified object.
     *
     * @return The object passed in.
     */
    public static JSONObj proj(JSONObj obj, CoordinateReferenceSystem crs, String srs) {
        if (srs == null && crs != null) {
            try {
                srs = CRS.lookupIdentifier(crs, false);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Unable to determine srs from crs: " + crs, e);
            }
        }
        
        if (crs != null && crs.getName() != null) {
            ReferenceIdentifier name = crs.getName();
            if (name instanceof GenericName) {
                obj.put("title", ((GenericName) name).tip().toString());
            } else {
                obj.put("title", name.toString());
            }
        }
        
        if (srs != null) {
            obj.put("srs", srs);
        }
        else {
            obj.put("srs", "UNKNOWN");
        }

        if (crs == null && srs != null) {
            try {
                crs = CRS.decode(srs);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Unable to determine crs from srs: " + srs, e);
            }
        }

        if (crs != null) {
            // type
            obj.put("type",
                    crs instanceof ProjectedCRS ? "projected" : crs instanceof GeographicCRS ? "geographic" : "other");

            // units
            String units = null;
            try {
                // try to determine from actual crs
                String unit = crs.getCoordinateSystem().getAxis(0).getUnit().toString();
                if ("ft".equals(unit) || "feets".equals(unit))
                    units = "ft";
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Unable to determine units from crs", e);
            }
            if (units == null) {
                // fallback: meters for projected, otherwise degrees
                units = crs instanceof ProjectedCRS ? "m" : "degrees";
            }
            obj.put("unit", units);
            obj.put("wkt", crs.toWKT());
        }

        return obj;
    }

    /**
     * Encodes a bounding box within the specified object.
     *
     * @return The object passed in.
     */
    public static JSONObj bounds(JSONObj obj, Envelope bbox) {
        obj.put("west", bbox.getMinX())
            .put("south", bbox.getMinY())
            .put("east", bbox.getMaxX())
            .put("north", bbox.getMaxY());

        if (!bbox.isNull()) {
            Coordinate center = bbox.centre();
            obj.putArray("center").add(center.x).add(center.y);
        }

        return obj;
    }

    /**
     * Decodes a bounding box within the specified object.
     *
     * The parsed envelope.
     */
    public static Envelope bounds(JSONObj obj) {
        return new Envelope(obj.doub("west"), obj.doub("east"), obj.doub("south"), obj.doub("north"));
    }

    /**
     * Decodes a projection within the specified object.
     *
     * @return The parsed projection, or null.
     *
     * @throws java.lang.IllegalArgumentException If the object has no 'srs' property or there was an error decoding
     * the srs.
     */
    public static CoordinateReferenceSystem crs(JSONObj obj) throws Exception {
        return Proj.get().crs(srs(obj));
    }

    /**
     * Decodes am srs within the specified projection object.
     *
     * @param obj JSON object with same structure as produced by {@link #proj(JSONObj,CoordinateReferenceSystem, String)}.
     *
     * @return The srs.
     *
     * @throws java.lang.IllegalArgumentException If the object has no 'srs' property.
     */
    public static String srs(JSONObj obj) {
        String srs = obj.str("srs");
        if (srs == null) {
            throw new IllegalArgumentException("Projection must have an 'srs' property");
        }
        return srs;
    }

    /**
     * Encodes a workspace within the specified object.
     *
     * @param obj The object to encode within.
     * @param workspace The workspace to encode.
     * @param namespace The namespace corresponding to the workspace.
     * @param isDefault Flag indicating whether the workspace is the default.
     *
     * @return The object passed in.
     */
    public static JSONObj workspace(JSONObj obj, WorkspaceInfo workspace, NamespaceInfo namespace, boolean isDefault) {
        obj.put("name", workspace.getName());
        if (namespace != null) {
            obj.put("uri", namespace.getURI());
        }
        obj.put("default", isDefault);
        return metadata(obj, workspace);
    }
    
    static Object title(LayerInfo layer) {
        ResourceInfo r = layer.getResource();
        return layer.getTitle() != null ? layer.getTitle() : r != null ? r.getTitle() : null;
    }

    static Object description(LayerInfo layer) {
        ResourceInfo r = layer.getResource();
        return layer.getAbstract() != null ? layer.getAbstract() : r != null ? r.getAbstract() : null;
    }

    public static JSONObj layer(JSONObj obj, PublishedInfo layer, HttpServletRequest req) {
        if( layer == null ){
            return obj;
        }
        if( layer instanceof LayerInfo){
            return layer( obj, (LayerInfo) layer, req );
        }
        else if ( layer instanceof LayerGroupInfo ){
            return layer( obj, (LayerGroupInfo) layer, req );
        }
        else {
            return obj;
        }
    }
    
    public static JSONObj layer(JSONObj obj, LayerGroupInfo group, HttpServletRequest req) {
        String wsName = group.getWorkspace().getName();
        obj.put("name", group.getName())
           .put("workspace", wsName)
           .put("url", IO.url(req,"/maps/%s/%s",wsName,group.getName()) )
           .put("title", group.getTitle() )
           .put("description", group.getAbstract() )
           .put("type", "map" )
           .put("group", group.getMode().name());
        
        proj(obj.putObject("proj"), group.getBounds().getCoordinateReferenceSystem(), null);
        bbox(obj.putObject("bbox"), group);
        
        return obj;
    }
    /**
     * Encodes a layer within the specified object.
     *
     * @return The object passed in.
     */
    @SuppressWarnings("unchecked")
    public static JSONObj layer(JSONObj obj, LayerInfo layer, HttpServletRequest req) {
        String wsName = layer.getResource().getNamespace().getPrefix();
        ResourceInfo r = layer.getResource();
        Type type = IO.Type.of(r); //IO.type(r);
        
        obj.put("name", layer.getName())
                .put("workspace", wsName)
                .put("title", title(layer))
                .put("description", description(layer))
                .put("type", type.toString());
        
        StoreInfo store = r.getStore();
        if( req != null ){
            obj.putObject("resource")
                .put("name",r.getName())
                .put("store",store.getName())
                .put("workspace",wsName)
                .put("url",
                     url(req, "/stores/%s/%s/%s",wsName, store.getName(),r.getName())
                );
        }
        
        JSONArr keywords = new JSONArr();
        keywords.raw().addAll( r.keywordValues() );
        obj.put("keywords", keywords);
        proj(obj.putObject("proj"), r.getCRS(), r.getSRS());
        bbox( obj.putObject("bbox"), r );
        
        if (r instanceof FeatureTypeInfo) {
            FeatureTypeInfo ft = (FeatureTypeInfo) r;
            FeatureType schema;
            try {
                schema = ft.getFeatureType();
                obj.put("geometry", geometry(schema));
                IO.schema(obj.putObject("schema"), schema, true );
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error looking up schema "+ft.getNativeName(), e);
            }
        }
        else if( r instanceof CoverageInfo) {
            obj.put("geometry", "raster");
            IO.schemaGrid(obj.putObject("schema"), ((CoverageInfo)r), true );
        }
        else if( r instanceof WMSInfo) {
            obj.put("geometry", "layer");
        }
        return metadata(obj, layer);
    }

    static String type(ResourceInfo r)  {
        if (r instanceof CoverageInfo) {
            return "raster";
        }
        else if (r instanceof FeatureTypeInfo){
            return "vector";
        }
        else if (r instanceof WMSLayerInfo){
            return "wms";
        }
        else {
            return "resource";
        }
    }

    static String geometry(FeatureType ft) {
        GeometryDescriptor gd = ft.getGeometryDescriptor();
        if (gd == null) {
            return "Vector";
        }
        @SuppressWarnings("unchecked")
        Geometries geomType = Geometries.getForBinding((Class<? extends Geometry>) gd.getType().getBinding());
        return geomType.getName();
    }
    
    public static JSONObj bbox( JSONObj bbox, LayerGroupInfo l ){
        ReferencedEnvelope bounds = l.getBounds();
        if (bounds != null) {
            bounds(bbox.putObject("native"), bounds );
            
            try {
                ReferencedEnvelope latLonBounds = bounds.transform(DefaultGeographicCRS.WGS84, true);
                bounds(bbox.putObject("lonlat"), latLonBounds);
            } catch (TransformException e) {
            } catch (FactoryException e) {
            }
        }
        return bbox;
    }
    
    public static JSONObj bbox( JSONObj bbox, ResourceInfo r ){
        if (r.getNativeBoundingBox() != null) {
            bounds(bbox.putObject("native"), r.getNativeBoundingBox());
        }
        else {
            // check if the crs is geographic, if so use lat lon
            if (r.getCRS() instanceof GeographicCRS && r.getLatLonBoundingBox() != null) {
                bounds(bbox.putObject("native"), r.getLatLonBoundingBox());
            }
        }

        if (r.getLatLonBoundingBox() != null) {
            bounds(bbox.putObject("lonlat"), r.getLatLonBoundingBox());
        }
        else {
            if (r.getNativeCRS() instanceof GeographicCRS && r.getLatLonBoundingBox() != null) {
                bounds(bbox.putObject("lonlat"), r.getLatLonBoundingBox());
            }
        }
        return bbox;
    }
    
    public static JSONObj schema( JSONObj schema, FeatureType type, boolean details){
        if( type != null ){
            schema.put("name", type.getName().getLocalPart() );
            schema.put("namespace", type.getName().getNamespaceURI() );
            schema.put("simple", type instanceof SimpleFeatureType );
            JSONArr attributes = schema.putArray("attributes");
            for( PropertyDescriptor d : type.getDescriptors() ){
                PropertyType t = d.getType();
                final String NAME = d.getName().getLocalPart();
                String kind;
                if (d instanceof GeometryDescriptor){
                    kind = "geometry";
                }
                else if( d instanceof AttributeDescriptor){
                    kind = "attribute";
                }
                else if (d instanceof AssociationDescriptor){
                    kind = "association";
                }
                else {
                    kind = "property";
                }
                JSONObj property = attributes.addObject()
                    .put("name", NAME )
                    .put("property", kind )
                    .put("type", t.getBinding().getSimpleName() );
                
                if( d instanceof GeometryDescriptor){
                    GeometryDescriptor g = (GeometryDescriptor) d;                    
                    proj( property.putObject("proj"), g.getCoordinateReferenceSystem(), null );
                }

                if( details){
                    property
                        .put("namespace", d.getName().getNamespaceURI() )
                        .put("description", t.getDescription() )
                        .put("min-occurs",d.getMinOccurs() )
                        .put("max-occurs",d.getMaxOccurs() )
                        .put("nillable",d.isNillable());
                
                    int length = FeatureTypes.getFieldLength(d);
                    if( length != FeatureTypes.ANY_LENGTH ){
                        property.put("length", length );
                    }
                    
                    if( d instanceof AttributeDescriptor){
                        AttributeDescriptor a = (AttributeDescriptor) d;
                        property.put("default-value", a.getDefaultValue() );
                    }
                    if( !t.getRestrictions().isEmpty() ){
                        JSONArr validate = property.putArray("validate");
                        for( Filter f : t.getRestrictions() ){
                            String cql;
                            try {
                                Filter clean = (Filter) f.accept( new DuplicatingFilterVisitor(){
                                    public PropertyName visit(PropertyName e, Object extraData ){
                                        String n = e.getPropertyName();
                                        return getFactory(extraData).property(
                                                ".".equals(n) ? NAME : n,
                                                e.getNamespaceContext());
                                    }
                                }, null );
                                cql = ECQL.toCQL(clean);
                            }
                            catch (Throwable ignore ){
                                ignore.printStackTrace();
                                cql = f.toString();
                            }
                            validate.add( cql );
                        }                    
                    }
                }
            }
        }
        return schema;
    }
    
    /**
     * Generate schema for GridCoverageSchema (see {@link FeatureUtilities#wrapGridCoverage}).
     */
    public static JSONObj schemaGrid( JSONObj schema, CoverageInfo info, boolean details ){
        if( info != null ){
            CoordinateReferenceSystem crs = info.getCRS() != null
                    ? info.getCRS()
                    : info.getNativeCRS();
            schemaGrid( schema, crs, details );
        }
        return schema;
    }
    public static JSONObj schemaGrid( JSONObj schema, CoordinateReferenceSystem crs, boolean details){
        schema.put("name", "GridCoverage" );
        schema.put("simple", true );
        JSONArr attributes = schema.putArray("attributes");
        JSONObj geom = attributes.addObject()
            .put("name", "geom" )
            .put("property", "geometry" )
            .put("type", "Polygon" );

        if( crs != null ){
            proj( geom.putObject("proj"), crs, null );
        }
        
        if( details ){
            geom
                .put("min-occurs",0)
                .put("max-occurs",1)
                .put("nillable",true)
                .put("default-value",null);   
        
        }
        JSONObj grid = attributes.addObject()
            .put("name", "grid" )
            .put("property", "attribute" )
            .put("type", "grid" );
        
        if( details ){
            grid
                .put("binding", "GridCoverage" )
                .put("min-occurs",0)
                .put("max-occurs",1)
                .put("nillable",true)
                .put("default-value",null);
        }
        return schema;
    }
    
    private static PrettyTime PRETTY_TIME = new PrettyTime();

    static JSONObj date(JSONObj obj, Date date) {
        String timestamp = new SimpleDateFormat(DATE_FORMAT).format(date);
        return obj.put("timestamp", timestamp).put("pretty", PRETTY_TIME.format(date));
    }

    /** Metadata: created and modified */
    static JSONObj metadata(JSONObj obj, Info i) {
        Date created = Metadata.created(i);
        if (created != null) {
            date(obj.putObject("created"), created);
        }
        Date modified = Metadata.modified(i);
        if (modified != null) {
            date(obj.putObject("modified"), modified);
        }
        return obj;
    }

    public static JSONObj error(JSONObj json, Throwable error) {
        if (error != null) {
            String message = null;
            JSONArr cause = new JSONArr();
            for (Throwable t : Throwables.getCausalChain(error)) {
                if (message == null && t.getMessage() != null) {
                    message = t.getMessage();
                }
                StringBuilder trace = new StringBuilder();
                for( StackTraceElement e : t.getStackTrace()){
                    trace.append( e.toString()).append('\n');
                }
                cause.addObject()
                    .put("exception", t.getClass().getSimpleName())
                    .put("message", t.getMessage())
                    .put("trace",trace.toString());
            }
            if (message == null) {
                message = error.getClass().getSimpleName();
            }
            json.put("message", message != null ? message : error.toString())
                .put("cause", cause)
                .put("trace",Throwables.getStackTraceAsString(error));
        }
        return json;
    }
    
    public static JSONObj param(JSONObj json, Parameter<?> p) {
        if (p != null) {
            String title = p.getTitle() != null ? p.getTitle().toString() : WordUtils.capitalize(p.getName());
            String description = p.getDescription() != null ? p.getDescription().toString() : null;

            JSONObj def = json.putObject(p.getName());
            def.put("title", title)
                .put("description",  description)
                .put("type", p.getType().getSimpleName())
                .put("default", safeValue(p.getDefaultValue()))
                .put("level", p.getLevel())
                .put("required", p.isRequired());
            
            if( !(p.getMinOccurs() == 1 && p.getMaxOccurs() == 1)){
                def.putArray("occurs")
                    .add( p.getMinOccurs())
                    .add(p.getMaxOccurs());
            }

            
            if (p.metadata != null) {
                for (String key : p.metadata.keySet()) {
                    if (Parameter.LEVEL.equals(key)) {
                        continue;
                    }
                    def.put(key, p.metadata.get(key));
                }
            }
        }
        return json;
    }
    public static void param(JSONObj json, Format g) {
        json.put("name","raster")
            .put("title","URL")
            .put("description",g.getDescription())
            .put("type",URL.class.getSimpleName())
            .put("default",null)
            .put("level","user")
            .put("required",true);
    }

    static private Object safeValue(Object value) {
        if(value == null){
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof java.util.TimeZone) {
            TimeZone zone = (TimeZone) value;
            return zone.getDisplayName();
        }
        return value.toString();
    }

    public static Object url(HttpServletRequest req, String path, Object ... args) {
        if (req == null) {
            return null;
        }
        String baseURL = ResponseUtils.baseURL(req);
        String relative = String.format("/api"+path, args );
        String resolved = ResponseUtils.buildURL(baseURL, relative, null, URLType.SERVICE);
        return resolved;
    }

    public static JSONObj ref(JSONObj obj, Ref ref) {
        obj.put("name", ref.name);
        if (ref.workspace != null) {
            obj.put("workspace", ref.workspace);
        }
        date(obj.putObject("modified"), ref.modified);
        return obj;
    }
    
    public static JSONObj files(JSONObj obj, File root, int depth) throws IOException {
        return files(obj, root, depth, new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return true;
            }});
    }

    //Listing of files
    public static JSONObj files(JSONObj obj, File root, int depth, FilenameFilter filter) throws IOException {
        
        BasicFileAttributes attr = Files.readAttributes(root.toPath(), BasicFileAttributes.class);
        
        obj.put("name", root.getName());
        obj.put("size", root.getTotalSpace());
        obj.put("modified", PRETTY_TIME.format(new Date(attr.lastModifiedTime().toMillis())));
        obj.put("added", PRETTY_TIME.format(new Date(attr.creationTime().toMillis())));
        
        if (root.isDirectory()) {
            obj.put("type", "folder");
            //Only go depth deep. Ignore depth if depth < 0;
            if (depth != 0) {
                JSONArr children = obj.putArray("children");
                for (File file : root.listFiles(filter)) {
                    JSONObj child = children.addObject();
                    files(child, file, depth-1, filter);
                }
            }
            
        } else {
            obj.put("type", "file");
        }
        //Optional? Could just implement . and .. in api
        if (filter.accept(root.getParentFile().getParentFile(), root.getParentFile().getName())) {
            obj.put("parent", root.getParentFile().getName());
        }
        
        return obj;
    }
}
