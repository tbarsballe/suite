.. _cartography.mbstyle.tutorial.line:

Styling a line layer
====================

The roads layer is a line layer, and therefore we use a `line layer <https://www.mapbox.com/mapbox-gl-js/style-spec/#layers-line>`_ to display it. 

Creating a new style
--------------------

#. Navigate to the GeoServer Styles list. Click the ``Add a new style`` option.

   Name this new style ``mbline`` and set the format to ``MBStyle``.

   Under the ``Generate a default style`` option, select ``Line`` and click the ``Generate`` link to create a default line style. 

.. TODO: If generate works, add instructions for generating a new MBStyle, else provide one we can paste.

   Click the ``Apply`` button, then navigate to the ``Layer Preview`` tab and select the ``roads`` layer to preview the style.

   .. figure:: ../../ysld/tutorial/img/line_default.png

      Default line style

   .. note:: Your default color may vary.

#. The style will look something like this:
   
   .. code-block:: json
   
      {
          "version": 8,
          "layers": [
              {
                  "type": "line",
                  "paint": {
                      "line-color": "#333333",
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
          "name": roads
          "layers": [
              {
                  "id": "roads"
                  "type": "line",
                  "paint": {
                      "line-color": "#333333",
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
          "name": "roads"
          "sources": {
              "test-roads": {
                  "url": "http://localhost:8080/geoserver/test/roads/wms",
                  "type": "vector"
              }
          },
          "layers": [
              {
                  "id": "roads",
                  "type": "line",
                  "source": "test-roads",
                  "source-layer": "roads",
                  "paint": {
                      "line-color": "#333333",
                  }
              }
          ],
      }

#. We can immediately see that there are far more roads than we need on this layer. Fortunately, the road data contains a scalerank attribute to help determine the importance of different roads. Add a `filter <https://www.mapbox.com/mapbox-gl-js/style-spec/#types-filter>` to only show roads with scalerank < 4. Then our style looks like:

   .. code-block:: json
      :emphasize-lines: 6-7

      {
          "version": 8,
          "name": "roads",
          "layers": [
              {
                  "id": "big",
                  "filter": ["<", "scalerank", 4],
                  "type": "line",
                  "paint": {
                      "line-color": "#333333",
                  }
              }
          ],
      }

Setting scale
-------------

If we zoom in, we want to see all the roads, not just those included in our filter. The ``minzoom`` and ``maxzoom`` properties can be used to show or hide layers depending on the zoom level. In order to determine what roads to show, we will again use the ``scalerank`` attribute of the data to add the following rules:

* If zoom is greater than 6, show features with ``scalerank < 4``
* If zoom is between 6 and 8, show features with ``scalerank < 8``
* If zoom is less than 8, show all features

#. Add a zoom function to the existing (``big``) rule, and add the other two rules (``medium`` and ``small``):

   .. code-block:: json
      :emphasize-lines: 8, 15-34
      
      {
          "version": 8,
          "name": "roads",
          "layers": [
              {
                  "id": "big",
                  "filter": ["<", "scalerank", 4],
                  "maxzoom": 6,
                  "type": "line",
                  "paint": {
                      "line-color": "#333333",
                      "line-width": 1
                  }
              },
              {
                  "id": "medium",
                  "filter": ["<", "scalerank", 8],
                  "minzoom": 6,
                  "maxzoom": 8,
                  "type": "line",
                  "paint": {
                      "line-color": "#333333",
                      "line-width": 1
                  }
              },
              {
                  "id": "small",
                  "minzoom": 8,
                  "type": "line",
                  "paint": {
                      "line-color": "#777777",
                      "line-width": 0.5
                  }
              }
          ],
      }

Differentiating features
------------------------

On the smaller scales, we want some differentiation between roads based on the feature's type. We will use the ``featurecla`` attribute for this:

#. Add a new layer for roads that have attribute ``featurecla = 'Ferry'``, and draw these roads with a blue line. Put this rule third in the list of four:

   .. code-block:: json
      :emphasize-lines: 26-33
      
      {
          "version": 8,
          "name": "roads",
          "layers": [
              {
                  "id": "big",
                  "filter": ["<", "scalerank", 4],
                  "maxzoom": 6,
                  "type": "line",
                  "paint": {
                      "line-color": "#333333",
                      "line-width": 1
                  }
              },
              {
                  "id": "medium",
                  "filter": ["<", "scalerank", 8],
                  "minzoom": 6,
                  "maxzoom": 8,
                  "type": "line",
                  "paint": {
                      "line-color": "#333333",
                      "line-width": 1
                  }
              },
              {
                  "id": "ferry",
                  "filter": ["==", "featurecla", "Ferry"],
                  "minzoom": 6,
                  "type": "line",
                  "paint": {
                      "line-color": "#00CCFF"
              },
              {
                  "id": "small",
                  "minzoom": 8,
                  "type": "line",
                  "paint": {
                      "line-color": "#777777",
                      "line-width": 0.5
                  }
              }
          ],
      }


#. Further modify this rule to use a dashed line. Add the following ``line-width`` and ``line-dasharray`` lines:

   .. code-block:: json
      :emphasize-lines: 6-7
      
      {
          "id": "ferry",
          "filter": ["==", "featurecla", "Ferry"],
          "minzoom": 6,
          "type": "line",
          "paint": {
              "line-color": "#00CCFF",
              "line-width": 2,
              "line-dasharray": [4, 6]
          }
      },

#. After adding the ferry rule, this is the view when zoomed in:

   .. figure:: img/line_ferry.png

      Ferry rule and other styles

Adding road casing
------------------

The ``line-gap-width`` property can be used to draw a line casing.

#. Add a new layer to draw expressways (``${expressway = 1}``) using 6 pixel black lines with a gap of 4 pixels. This will be our outline:

   .. code-block:: json

      {
          "id": "expressway",
          "filter": ["==", "expressway", 1],
          "minzoom": 6,
          "type": "line",
          "paint": {
              "line-color": "#000000",
              "line-width": 6,
              "line-gap-width": 4,
              "line-cap": "round"
          }
      },

#. Below this rule, add another new layer to draw expressways using 4 pixel orange lines with round ends:

   .. code-block:: json

      {
          "id": "inner",
          "filter": ["==", "expressway", 1],
          "minzoom": 6,
          "type": "line",
          "paint": {
              "line-color": "#FFCC00",
              "line-width": 4,
              "line-cap": "round"
          }
      },

#. After adding the ferries and expressways layers, this is the view when zoomed in:

   .. figure:: img/line_intermediate.png

      Road casing and other styles

#. Now that we have these rules for special types of "roads", we want to make sure our ``medium`` layer does not also draw lines for these special roads. We can add a filter to the rule to exclude these from the layer (``!=`` means "not equal to"):

   .. code-block:: json
      :emphasize-lines: 3

      {
          "id": "medium",
          "filter": ["all", ["<", "scalerank", 8], ["!=", "expressway", 1], ["!=", "featurecla", "Ferry"]],
          "minzoom": 6,
          "type": "line",
          "paint": {
              "line-color": "#333333",
              "line-width": 1
          }
      },

#. Add a similar filter to the ``small`` layer.

Final style
-----------

After all these changes, the final style should look like this:

.. literalinclude:: files/mbtut_line.json
   :language: json

The resulting map shows different levels of road detail at different zoom levels:

.. figure:: img/line_all_zoom_1.png

   Completed Roads layer (zoomed out)

.. figure:: img/line_all_zoom_2.png

   Completed Roads layer (intermediate zoom)

.. figure:: img/line_all_zoom_3.png

   Completed Roads layer (zoomed in)

.. note:: :download:`Download the final line style <files/mbtut_line.json>`

Continue on to :ref:`cartography.mbstyle.tutorial.poolygon`.
