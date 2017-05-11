.. _cartography.mbstyle.tutorial.point:

Styling a point layer
=====================

The populated places layer is a point layer, so we will be using `circle layers <https://www.mapbox.com/mapbox-gl-js/style-spec/#layers-circle>`_ and `symbol layers <https://www.mapbox.com/mapbox-gl-js/style-spec/#layers-symbol>`_.

reating a new style
--------------------

#. Navigate to the GeoServer Styles list. Click the ``Add a new style`` option.

   Name this new style ``mbpoint`` and set the format to ``MBStyle``.

   Under the ``Generate a default style`` option, select ``Point`` and click the ``Generate`` link to create a default point style. 

.. TODO: If generate works, add instructions for generating a new MBStyle, else provide one we can paste.

   Click the ``Apply`` button, then navigate to the ``Layer Preview`` tab and select the ``places`` layer to preview the style.

   .. figure:: ../../ysld/tutorial/img/point_default.png

      Default point style

   .. note:: Your default color may vary.

#. The style will look something like this:
   
   .. code-block:: json
   
      {
        "version": 8,
        "layers": [
            {
                "type": "circle",
                "paint": {
                    "circle-color": "#333333",
                }
            }
        ]
      }

Name and id
-----------

The style can be given a ``name`` parameter, and layers within the style can be given an ``id`` parameter. ``name`` is a machine reference to the style element, but may also be displayed. ``id`` is a machine reference to the layer. Both should be **lower case** and contain **no spaces**. 

#. Modify the name and id elements in the default style:

   .. code-block::json
      :emphasize-lines: 3, 6
      
      {
        "version": 8,
        "name": "places",
        "layers": [
            {
                "id": "places"
                "type": "circle",
                "paint": {
                    "circle-color": "#333333",
                }
            }
        ]
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
          "name": "places"
          "sources": {
              "test-places": {
                  "url": "http://localhost:8080/geoserver/test/places/wms",
                  "type": "vector"
              }
          },
          "layers": [
              {
                  "id": "places",
                  "type": "circle",
                  "source": "test-places",
                  "source-layer": "places",
                  "paint": {
                      "circle-color": "#333333",
                  }
              }
          ],
      }

Adding filters and labels
-------------------------

#. There are a lot of points in this data set, and we don't want to draw all of them. Use the ``ADM0CAP`` attribute and a filter to show only points that correspond to capital cities (``ADM0CAP = 1``):

   .. code-block:: json
      :emphasize-lines: 7

      {
        "version": 8,
        "name": "places",
        "layers": [
            {
                "id": "places",
                "filter": ["==", "ADM0CAP", 1],
                "type": "circle",
                "paint": {
                    "circle-color": "#333333",
                    "circle-radius": 3
                }
            }
        ]
      }

#. Add a symbol layer referencing the ``NAME`` attribute to display the names of the cities:

   .. code-block:: yaml
      :emphasize-lines: 14-22

      {
        "version": 8,
        "name": "places",
        "layers": [
            {
                "id": "places",
                "filter": ["==", "ADM0CAP", 1],
                "type": "circle",
                "paint": {
                    "circle-color": "#333333",
                    "circle-radius": 3
                }
            },
            {
                "id": "places-label",
                "filter": ["==", "ADM0CAP", 1],
                "type": "symbol",
                "layout": {
                    "text-field": "{NAME}",
                    "text-anchor": "bottom-left",
                    "text-offset": [3,2]
                }
            }
        ]
      }


#. We now have a reasonably sized set of labeled points.

   .. figure:: ../../ysld/tutorial/img/point_simple_label.png

      Capital cities

Refining the style
------------------

Now, lets do some styling. The circle layer only allows for circular points, so we use a `symbol layer <https://www.mapbox.com/mapbox-gl-js/style-spec/#layers-symbol>`. In order to display symbols, we also need to define a spritesheet.

A spritesheet consists of a png file containing a number of icons, plus a json file defining the name and bounds of each icon. Download :download:`sprites.png <files/sprites.png>` and :download:`sprites.json <files/sprites.json>` and copy them into the styles folder of your geoserver data directory.

We can now define a top-level ``sprites`` parameter with the URL to the spritesheet. This allows us to refer to these sprites in our style. 

#. Modify the existing symbol layer with the following:

   .. code-block:: json
      :emphasize-lines: 4,23-24

      {
        "version": 8,
        "name": "places",
        "sprite": "http://localhost:8080/geoserver/styles/sprites"
        "layers": [
            {
                "id": "places",
                "filter": ["==", "ADM0CAP", 1],
                "type": "circle",
                "paint": {
                    "circle-color": "#333333",
                    "circle-radius": 3
                }
            },
            {
                "id": "places-label",
                "filter": ["==", "ADM0CAP", 1],
                "type": "symbol",
                "layout": {
                    "text-field": "{NAME}",
                    "text-anchor": "bottom-left",
                    "text-offset": [3,2],
                    "icon-image": "capitol",
                    "icon-size": 7
                }
            }
        ]
      }

   This draws the ``capitol`` icon (a black star bounded by a circle) 7 pixels in height.

   .. figure:: ../../ysld/tutorial/img/point_style_label.png

      Capital cities with labels

#. Since this data set contains population attributes, we can scale the size of the points based on population. Use ``${log(POP_MAX)/log(4)}`` in the ``size`` parameter to get a relative scale without too much variation in point size. As before, make the circle symbolizer one pixel larger than the star:

   .. code-block:: yaml
      :emphasize-lines: 9,15

      name: places
      title: Populated places style
      feature-styles:
      - name: name
        rules:
        - filter: ${ADM0CAP = 1}
          symbolizers:
          - point:
              size: ${log(POP_MAX)/log(4)}
              symbols:
              - mark:
                  shape: star
                  fill-color: '#000000'
          - point:
              size: ${log(POP_MAX)/log(4)+1}
              symbols:
              - mark:
                  shape: circle
                  stroke-color: '#000000'
          - text:
              label: ${NAME}
              font-weight: bold
              displacement: [5,4]
              x-labelPriority: ${10-LABELRANK}

   .. figure:: ../../ysld/tutorial/img/point_size_label.png

      Variable symbol sizes

Adding scale
------------

To improve the display further, we can add scale rules.

#. Split the single rule into three rules:

   * A 3 pixel black circle for the features when zoomed out past 100,000,000 (``1e8``).
   * The star/circle combo as done in the previous section when zoomed in past 100,000,000 (``1e8``).
   * The labels only when zoomed in past 50,000,000 (``5e7``).

   This results in the following style:

   .. code-block:: yaml
      :emphasize-lines: 6-37

      name: places
      title: Populated places style
      feature-styles:
      - name: name
        rules:
        - scale: [1e8,max]
          filter: ${ADM0CAP = 1}
          symbolizers:
          - point:
              size: 3
              symbols:
              - mark:
                  shape: circle
                  fill-color: '#000000'
        - scale: [min,1e8]
          filter: ${ADM0CAP = 1}
          symbolizers:
          - point:
              size: ${log(POP_MAX)/log(4)}
              symbols:
              - mark:
                  shape: star
                  fill-color: '#000000'
          - point:
              size: ${log(POP_MAX)/log(4)+1}
              symbols:
              - mark:
                  shape: circle
                  stroke-color: '#000000'
        - scale: [min,5e7]
          filter: ${ADM0CAP = 1}  
          symbolizers:
          - text:
              label: ${NAME}
              font-weight: bold
              displacement: [5,4]
              x-labelPriority: ${10-LABELRANK}

#. We can show all the cities that are currently hidden when we zoom in further. One way to do this is by adding rules with an ``else`` clause and small scales. As with the capital cities, we will selectively show labels depending on the scale. To do this: add these two rules to the bottom of the style:

   .. code-block:: yaml

        - scale: [5e6,1e7]
          else: true
          symbolizers:
            - point:
                size: ${log(POP_MAX)/log(4)}
                symbols:
                - mark:
                    shape: circle
                    stroke-color: '#000000'
                    fill-color: '#777777'
                    fill-opacity: 0.5
        - scale: [min,5e6]
          else: true
          symbolizers:
            - point:
                size: ${log(POP_MAX)/log(4)+1}
                symbols:
                - mark:
                    shape: circle
                    stroke-color: '#000000'
                    fill-color: '#777777'
                    fill-opacity: 0.5
            - text:
                label: ${NAME}
                displacement: [5,4]
                x-labelPriority: ${10-LABELRANK}


Final style
-----------

The full style is now:

.. literalinclude:: files/ysldtut_point.ysld
   :language: yaml

After these modifications, we have a much nicer display at different zoom levels:

.. figure:: img/point_zoom_2.png

   Cities (zoomed out)

.. figure:: img/point_zoom_3.png

   Cities (intermediate zoom)

.. figure:: img/point_zoom_5.png

   Cities (zoomed in)

.. note:: :download:`Download the final point style <files/ysldtut_point.ysld>`

Continue on to :ref:`cartography.ysld.tutorial.raster`.
