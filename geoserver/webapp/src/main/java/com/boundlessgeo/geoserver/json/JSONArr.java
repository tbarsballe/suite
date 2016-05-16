/* (c) 2014 Boundless, http://boundlessgeo.com
 * This code is licensed under the GPL 2.0 license.
 */
package com.boundlessgeo.geoserver.json;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;

import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Iterator;

/**
 * Wrapper object for {@link org.json.simple.JSONArray}.
 */
public class JSONArr extends JSONWrapper<JSONArray> implements Iterable<Object> {

    public JSONArr() {
        this(new JSONArray());
    }

    public JSONArr(JSONArray obj) {
        super(obj);
    }

    /**
     * Gets the raw item at the specified index.
     */
    public Object at(int i) {
        return raw.get(i);
    }

    /**
     * Gets the item at the specified index as a string.
     */
    public String str(int i) {
        return to(at(i), String.class);
    }

    /**
     * Gets the item at the specified index as a string.
     */
    public Double doub(int i) {
        return to(at(i), Double.class);
    }

    /**
     * Gets the item at the specified index as a integer.
     */
    public Integer integer(int i) {
        return to(at(i), Integer.class);
    }

    /**
     * Gets the  item at the specified index as an object wrapper.
     */
    public JSONObj object(int i) {
        return JSONWrapper.wrap(at(i)).toObject();
    }

    /**
     * Adds a raw object ot the underlying array.
     *
     * @return This array.
     */
    public JSONArr add(Object obj) {
        raw.add(obj);
        return this;
    }

    /**
     * Adds a new object to the array and returns a wrapper.
     *
     * @return The new object wrapper.
     */
    public JSONObj addObject() {
        JSONObj obj = new JSONObj();
        raw.add(obj);
        return obj;
    }

    @Override
    public int size() {
        return raw.size();
    }

    /**
     * Returns an iterator over the array.
     * <p>
     * JSON objects are wrapped upon return, primitives are returned as is.
     * </p>
     */
    @Override
    public Iterator<Object> iterator() {
        return Iterators.transform(raw.iterator(), new Function<Object,Object>() {
            @Nullable
            @Override
            public Object apply(@Nullable Object o) {
                return JSONWrapper.wrapOrSelf(o);
            }
        });
    }

    public Iterable<JSONObj> objects() {
        return new Iterable<JSONObj>() {
            @Override
            public Iterator<JSONObj> iterator() {
                return Iterators.transform(JSONArr.this.iterator(), new Function<Object, JSONObj>() {
                    @Nullable
                    @Override
                    public JSONObj apply(@Nullable Object input) {
                        return (JSONObj) input;
                    }
                });
            }
        };
    }
    
    @Override
    void write(Writer out) throws IOException {
        if (raw == null) {
            out.write("null");
        } else {
            boolean first = true;
            Iterator iter = iterator();
            out.write('[');
            while (iter.hasNext()) {
                if (first)
                    first = false;
                else
                    out.write(',');

                Object value = iter.next();
                if (value == null) {
                    out.write("null");
                    continue;
                }
                value = wrapOrSelf(value);
                JSONWrapper.write(value, out);
            }
            out.write(']');

        }
        out.flush();
    }
}
