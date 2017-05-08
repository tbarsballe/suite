.. _cartography.mbstyle.tutorial.polygon:

Styling a polygon layer
=======================

The countries layer is a polygon layer, and therefore we use a `fill layer <https://www.mapbox.com/mapbox-gl-js/style-spec/#layers-fill>`_ to display it. 

Creating a new style
--------------------

#. Navigate to the GeoServer Styles list. Click the ``Add a new style`` option.

   Name this new style ``mbpolygon`` and set the format to ``MBStyle``.

   Under the ``Generate a default style`` option, select ``Point`` and click the ``Generate`` link to create a default polygon style. 

.. TODO: If generate works, add instructions for generating a new MBStyle, else provide one we can paste.

   Click the ``Apply`` button, then navigate to the ``Layer Preview`` tab and select the ``countries`` layer to preview the style.

   .. figure:: ../../ysld/tutorial/img/poly_default.png

      Default polygon style

   .. note:: Your default color may vary.

#. The style will look something like this:
   
   .. code-block:: json
   
      {
          "version": 8,
          "layers": [
              {
                  "type": "fill",
                  "paint": {
                      "fill-color": "#FFFF00",
                      "fill-outline-color":"#000000"
                  }
              }
          ],
      }

Name and id
-----------

The style can be given a ``name`` parameter, and layers within the style can be given an ``id`` parameter. ``name`` is a machine reference to the style element, but may also be displayed. ``id`` is a machine reference to the layer. Both should be **lower case** and contain **no spaces**. 

#. Modify the name and id elements in the default style:

   .. code-block:: yaml
      :emphasize-lines: 3, 6
      
      {
          "version": 8,
          "name": "countries",
          "layers": [
              {
                  "id": "countries",
                  "type": "fill",
                  "paint": {
                      "fill-color": "#FFFF00",
                      "fill-outline-color":"#000000"
                  }
              }
          ],
      }

Sources
-------

.. TODO: Move this to the end until it is actually supported by geoserver?

MBStyles have a `sources <https://www.mapbox.com/mapbox-gl-js/style-spec/#root-sources>`_ element, which describes the data to be rendered by the style. This is used by client applications to retrieve vector data.

.. note:: GeoServer currently ignores the sources element, but supports it for compatibility with client-side styles. As such, the sources element will not be used for this tutorial

#. A sources element for the countries layer would look like this:

   .. code-block:: json
      :emphasize-lines: 4-8, 14-15
   
      {
          "version": 8,
          "name": "countries"
          "sources": {
              "test-countries": {
                  "url": "http://localhost:8080/geoserver/test/countries/wms",
                  "type": "vector"
              }
          },
          "layers": [
              {
                  "id": "countries",
                  "type": "fill",
                  "source": "test-countries",
                  "source-layer": "countries",
                  "paint": {
                      "fill-color": "#FFFF00",
                      "fill-outline-color":"#000000"
                  }
              }
          ],
      }

Setting basic styling
---------------------

Fill layers provide options for styling the fill (inside) of features. While they do provide a fill-outline parameter for setting an outline color, more advanced outline styling requires a line layer.

.. TODO: Move line section of the tutorial before polygon, so uses will already have experience with line layers

#. Fill styling is defined primarily by ``fill-color`` and ``fill-opacity``. Change the style to use a 50% transparent brown fill:

   .. code-block:: json
      :emphasize-lines: 5-6
    
      {
          "type": "fill",
          "id": "countries",
          "paint": {
              "fill-color": "#EFEFC3",
              "fill-opacity": 0.5
          }
      }

#. Advanced stroke styling can be added by using a seperate line layer. Line styling is defined primarily by ``line-width``, ``line-color``, and ``line-opacity``. Change the style to use a 0.5 pixel gray stroke:

   .. note:: With no opacity set, the default will be 100% opaque.

   .. code-block:: json
    
      {
          "type": "line",
          "id": "countries-line",
          "paint": {
              "line-color": "#777777",
              "line-width": 0.5
          }
      }


#. Additional styling options are available for both stroke and fill, and can be found in the `fill layer <https://www.mapbox.com/mapbox-gl-js/style-spec/#layers-fill>` and `line layer <https://www.mapbox.com/mapbox-gl-js/style-spec/#layers-line>` sections of the MapBox specification respectively. Use ``line-dasharray`` to change the line style to a dashed line of 4 pixels with 4 pixel gaps.

   .. code-block:: json
      :emphasize-lines: 7
    
      {
          "type": "line",
          "id": "countries-line",
          "paint": {
              "line-color": "#777777",
              "line-width": 0.5,
              "line-dasharray": [4, 4]
          }
      }

#. The complete style after these changes will be:

   .. code-block:: json
      
      {
          "version": 8,
          "name": "countries",
          "layers": [
              {
                  "type": "fill",
                  "id": "countries-fill",
                  "paint": {
                      "fill-color": "#EFEFC3",
                      "fill-opacity": 0.5
                  }
              },
              {
                  "type": "line",
                  "id": "countries-line",
                  "paint": {
                      "line-color": "#777777",
                      "line-width": 0.5,
                      "line-dasharray": [4, 4]
                  }
              }
          ]
      }

#. And the layer now will look like this:

   .. figure:: ../../ysld/tutorial/img/poly_basic.png

      Basic styled polygons

Adding labels
-------------

Labels can be applied to any layer using a `symbol layer <https://www.mapbox.com/mapbox-gl-js/style-spec/#layers-symbol>`_. Typically you will want to use some data attribute as the label text, usually a name.

#. Add a symbol layer with a basic label using the ``text-field`` parameter and the ``NAME`` attribute:
   
   .. code-block:: yaml
      :emphasize-lines: 22-28
   
      {
          "version": 8,
          "name": "countries",
          "layers": [
              {
                  "type": "fill",
                  "id": "countries-fill",
                  "paint": {
                      "fill-color": "#EFEFC3",
                      "fill-opacity": 0.5
                  }
              },
              {
                  "type": "line",
                  "id": "countries-line",
                  "paint": {
                      "line-color": "#777777",
                      "line-width": 0.5,
                      "line-dasharray": [4, 4]
                  }
              },
              {
                  "type": "symbol",
                  "id": "countries-symbol",
                  "layout": {
                      "text-field": "{NAME}"
                  }
              }
          ]
      }

#. After this change, the map will look like this:

   .. figure:: ../../ysld/tutorial/img/poly_label_basic.png

      Basic labels

Styling labels
--------------

The default labeling parameters are not ideal, but a number of styling options are available. 

#. Add the following attributes to the symbol layer layout object:

   .. list-table::
      :class: non-responsive
      :widths: 40 60 
      :header-rows: 1

      * - Parameter
        - Description
      * - ``"text-transform": "uppercase"``
        - Change the label text to uppercase
      * - ``text-size: 14``
        - Change the font size to 14
      * - ``"text-font": ["SansSerif"]``
        - Change the font to SansSerif
      * - ``text-max-width: 100``
        - Wrap any labels wider than 100 pixels

.. TODO: Add explaination of glyphs for defining font sources

.. TODO: Add support for bold (presumably its own font)

.. TODO: Add support for label priority? MBStyle doesn't really have this concept?

#. Add the following attributes to the symbol layer paint object:

   .. list-table::
      :class: non-responsive
      :widths: 40 60 
      :header-rows: 1
      * - ``text-color: '#333333'``
        - Change the font color to dark gray

#. With the label styling, the style now looks like this:

   .. code-block:: json
      :emphasize-lines: 27-34
   
      {
          "version": 8,
          "name": "countries",
          "layers": [
              {
                  "type": "fill",
                  "id": "countries-fill",
                  "paint": {
                      "fill-color": "#EFEFC3",
                      "fill-opacity": 0.5
                  }
              },
              {
                  "type": "line",
                  "id": "countries-line",
                  "paint": {
                      "line-color": "#777777",
                      "line-width": 0.5,
                      "line-dasharray": [4, 4]
                  }
              },
              {
                  "type": "symbol",
                  "id": "countries-symbol",
                  "layout": {
                      "text-field": "{NAME}",
                      "text-transform": "uppercase",
                      "text-size": 14,
                      "text-font": ["Open Sans Regular"],
                      "text-max-width": 100
                  },
                  "paint": {
                      "text-color": "#333333"
                  }
              }
          ]
      }

  And the labels now appear much clearer:

     .. figure:: ../../ysld/tutorial/img/poly_label_styled.png

        Styled labels

Adding filters
--------------

Suppose we wish to display different colors for each country. The countries layer contains an attribute called ``MAPCOLOR7``, which assigns each country a number from 1 to 7, such that no adjacent countries have the same number. We can use this attribute to control what color a country is using `filters <https://www.mapbox.com/mapbox-gl-js/style-spec/#types-filter>`_. Filters apply a condition to a layer, so that the layer is only drawn if the filter evaluates to true.

#. Replace the rule containing the polygon symbolizer with seven rules, corresponding to the seven possibilities of values for ``MAPCOLOR7``. For each value, set the ``fill-color`` to the following:

   .. list-table::
      :class: non-responsive
      :widths: 40 60 
      :header-rows: 1

      * - Filter
        - Parameter
      * - ``MAPCOLOR7 = 1``
        - ``"fill-color": "#FFC3C3"``
      * - ``MAPCOLOR7 = 2``
        - ``"fill-color": "#FFE3C3"``
      * - ``MAPCOLOR7 = 3``
        - ``"fill-color": "#FFFFC3"``
      * - ``MAPCOLOR7 = 4``
        - ``"fill-color": "#C3FFE3"``
      * - ``MAPCOLOR7 = 5``
        - ``"fill-color": "#C3FFFF"``
      * - ``MAPCOLOR7 = 6``
        - ``"fill-color": "#C3C3FF"``
      * - ``MAPCOLOR7 = 7``
        - ``"fill-color": "#FFC3FF"``

#. After adding the filters, the style will look like:
   
   .. code-block:: json
      :emphasize-lines: 5-67

      {
          "version": 8,
          "name": "countries",
          "layers": [
              {
                  "type": "fill",
                  "filter": ["==", "MAPCOLOR7", 1],
                  "id": "countries-fill-1",
                  "paint": {
                      "fill-color": "#FFC3C3",
                      "fill-opacity": 0.5
                  }
              },
              {
                  "type": "fill",
                  "filter": ["==", "MAPCOLOR7", 2],
                  "id": "countries-fill-2",
                  "paint": {
                      "fill-color": "#FFE3C3",
                      "fill-opacity": 0.5
                  }
              },
              {
                  "type": "fill",
                  "filter": ["==", "MAPCOLOR7", 3],
                  "id": "countries-fill-3",
                  "paint": {
                      "fill-color": "#FFFFC3",
                      "fill-opacity": 0.5
                  }
              },
              {
                  "type": "fill",
                  "filter": ["==", "MAPCOLOR7", 4],
                  "id": "countries-fill-4",
                  "paint": {
                      "fill-color": "#C3FFE3",
                      "fill-opacity": 0.5
                  }
              },
              {
                  "type": "fill",
                  "filter": ["==", "MAPCOLOR7", 5],
                  "id": "countries-fill-5",
                  "paint": {
                      "fill-color": "#C3FFFF",
                      "fill-opacity": 0.5
                  }
              },
              {
                  "type": "fill",
                  "filter": ["==", "MAPCOLOR7", 6],
                  "id": "countries-fill-6",
                  "paint": {
                      "fill-color": "#C3C3FF",
                      "fill-opacity": 0.5
                  }
              },
              {
                  "type": "fill",
                  "filter": ["==", "MAPCOLOR7", 7],
                  "id": "countries-fill-7",
                  "paint": {
                      "fill-color": "#FFC3FF",
                      "fill-opacity": 0.5
                  }
              },
              {
                  "type": "line",
                  "id": "countries-line",
                  "paint": {
                      "line-color": "#777777",
                      "line-width": 0.5,
                      "line-dasharray": [4, 4]
                  }
              },
              {
                  "type": "symbol",
                  "id": "countries-symbol",
                  "layout": {
                      "text-field": "{NAME}",
                      "text-transform": "uppercase",
                      "text-size": 14,
                      "text-font": ["Open Sans Regular"],
                      "text-max-width": 100
                  },
                  "paint": {
                      "text-color": "#333333"
                  }
              }
          ]
      }


   .. figure:: ../../ysld/tutorial/img/poly_label_color.png

      Adjacent countries will not have the same color

Compacting thematic styles with transformation functions
--------------------------------------------------------

While filters are very useful, the required syntax is still quite long, and much of the content is redundant. The exact same functionality can be accomplished much more concisely using a `categorical function <https://www.mapbox.com/mapbox-gl-js/style-spec/#types-function>`_.

#. Remove all of the polygon rules and the variable at the top, and replace with our original rule:
   
   .. code-block:: yaml
      :emphasize-lines: 5-12
      
      {
          "version": 8,
          "name": "countries",
          "layers": [
              {
                  "type": "fill",
                  "id": "countries-fill",
                  "paint": {
                      "fill-color": "#EFEFC3",
                      "fill-opacity": 0.5
                  }
              },
              {
                  "type": "line",
                  "id": "countries-line",
                  "paint": {
                      "line-color": "#777777",
                      "line-width": 0.5,
                      "line-dasharray": [4, 4]
                  }
              },
              {
                  "type": "symbol",
                  "id": "countries-symbol",
                  "layout": {
                      "text-field": "{NAME}",
                      "text-transform": "uppercase",
                      "text-size": 14,
                      "text-font": ["Open Sans Regular"],
                      "text-max-width": 100
                  },
                  "paint": {
                      "text-color": "#333333"
                  }
              }
          ]
      }

#. Change the ``fill-color`` to the following function:
   
   .. code-block:: json
      :emphasize-lines: 5-17
      
      {
          "type": "fill",
          "id": "countries-fill",
          "paint": {
              "fill-color": {
                  "property": "MAPCOLOR7",
                  "type": "categorical",
                  "stops": [
                      [1, "#FFC3C3"], 
                      [2, "#FFE3C3"],
                      [3, "#FFFFC3"], 
                      [4, "#C3FFE3"],
                      [5, "#C3FFFF"], 
                      [6, "#C3C3FF"],
                      [7, "#FFC3FF"],
                  ]
              },
              "fill-opacity": 0.5
          }
      }

This sets the ``fill-color`` based on the value of ``MAPCOLOR7``, according to the key-value pairs in the ``recode`` function: if ``MAPCOLOR7 = 1``, set to ``'#FFC3C3'``, if ``MAPCOLOR7 = 2`` set to ``'#FFE3E3'``, etc.

It should be noted that this will produce the *exact same output* as in the previous section.

Final style
-----------

The full style now looks like this:

.. literalinclude:: files/mbtut_poly.json
   :language: json

.. note:: :download:`Download the final polygon style <files/mbtut_poly.json>`

Continue on to :ref:`cartography.mbstyle.tutorial.point`.
