.. _cartography.ysld.tutorial.polygon:

Styling a polygon layer
=======================

The Countries layer is a polygon layer, and therefore we use a :ref:`polygon symbolizer <cartography.ysld.reference.symbolizers.polygon>` to display it. 

Viewing the existing style
--------------------------

#. In the layers tab of the Composer, click on the style option for the ``ne_10m_admin_0_countries`` layer to go to the style edit page. A simple default style is already associated with this layer.

   .. figure:: img/poly_default.png

      Default polygon style

   .. note:: Your default color may vary.

#. The default style will look something like this:
   
   .. code-block:: yaml
   
      name: Default Styler
      title: A yellow polygon style
      feature-styles:
      - name: name
        rules:
        - title: yellow polygon
          symbolizers:
          - polygon:
              stroke-color: '#000000'
              stroke-width: 0.5
              fill-color: '#FFFF00'

Name and Title
--------------

The feature style and individual elements within the style can be given ``name`` and ``title`` parameters. ``name`` is a machine reference to the style element and is not intended to be displayed. It should be **lower case** and contain **no spaces**. ``title`` is a human-readable description of a style element, and will be displayed by client appliations. 

#. Modify the name and title elements in the default style:

   .. code-block:: yaml
      :emphasize-lines: 1-3, 7
      
      name: countries
      title: Countries style
      abstract: countries of the world
      feature-styles:
      - name: name
        rules:
        - title: Countries
          symbolizers:
          - polygon:
              stroke-color: '#000000'
              stroke-width: 0.5
              fill-color: '#FFFF00'


Setting basic styling
---------------------

Polygon symbolizers provide options for styling both fill (inside) and stroke (outline) of features.

#. Stroke styling is defined primarily by ``stroke-width``, ``stroke-color``, and ``stroke-opacity``. Change the style to use a 0.5 px grey stroke:

.. code-block:: yaml
      :emphasize-lines: 5-6
    
      rules:
      - title: Countries 
        symbolizers:
          polygon:
            stroke-color: '#777777'
            stroke-width: 0.5

#. Fill styling is defined primairly by ``fill-color`` and ``fill-opacity``. Change the style to use a 50% transparent brown fill:

.. code-block:: yaml
      :emphasize-lines: 7-8
    
      rules:
      - title: Countries 
        symbolizers:
          polygon:
            stroke-color: '#777777'
            stroke-width: 0.5
            fill-color: '#EFEFC3'
            fill-opacity: 0.5

#. Additional styling options are available for both stroke and fill, and can be found in the :ref:`symbolizer reference <cartography.ysld.reference.symbolizers>`. Use ``stroke-dasharray`` to change the line style to a dashed line of 4px with 4px gaps.

#. The complete style after these changes will be:

   .. code-block:: yaml
      :emphasize-lines: 11
      
      name: countries
      title: Countries style
      abstract: countries of the world
      feature-styles:
      - name: name
        rules:
        - symbolizers:
          - polygon:
              stroke-color: '#777777'
              stroke-width: 0.5
              stroke-dasharray: '4 4'
              fill-color: '#EFEFC3'
              fill-opacity: 0.5

   And the layer now will look like this:

   .. figure:: img/poly_basic.png

      Basic styled polygons

Adding labels
-------------

Labels can be applied to any layer using a :ref:`text symbolizer <cartography.ysld.reference.symbolizers.text>`. Typically you will want to use some data attribute as the label text, usually a name.

#. Add a text symbolizer with a basic label using the ``NAME`` attribute:
   
   .. code-block:: yaml
      :emphasize-lines: 14-15
   
      name: countries
      title: Countries style
      abstract: countries of the world
      feature-styles:
      - name: name
        rules:
        - symbolizers:
          - polygon:
              stroke-color: '#777777'
              stroke-width: 0.5
              stroke-dasharray: '4 4'
              fill-color: '#EFEFC3'
              fill-opacity: 0.5
          - text:
              label: ${NAME}

#. After this change, the map will look like:

   .. figure:: img/poly_label_basic.png

      Basic labels

Styling labels
--------------

The default labeling parameters are not ideal, but a number of styling options are available. 

#. Add the following attributes to the text symbolizer:

   .. list-table::
      :class: non-responsive
      :widths: 40 60 
      :header-rows: 1

      * - Parameter
        - Description
      * - ``label: ${strToUpperCase(NAME)}``
        - Change the label text to uppercase
      * - ``font-size: 14``
        - Change the font size to 14
      * - ``font-family: SansSerif``
        - Change the font to SansSerif
      * - ``font-weight: bold``
        - Make the font bold
      * - ``fill-color: '#333333'``
        - Change the font color to dark gray

   This gives a much nicer font style, but the label placement is still poor. 

#. Add some additional options to fix this:

   .. list-table::
      :class: non-responsive
      :widths: 40 60 
      :header-rows: 1

      * - Parameter
        - Description
      * - ``x-autoWrap: 100``
        - Wrap any labels wider than 100 pixels
      * - ``x-maxDisplacement: 200``
        - Allow labels to shift up to 200 pixels to maintain best placement
      * - ``x-goodnessOfFit: 0.8``
        - Only show labels with 0.8 or better fit
      * - ``x-labelPriority: ${10-LABELRANK}``
        - Select labels based on priority (uses the ``LABELRANK`` attribute of the data to determine this).

With the label styling, the style now looks like this:

   .. code-block:: yaml
      :emphasize-lines: 16-23
   
      name: countries
      title: Countries style
      abstract: countries of the world
      feature-styles:
      - name: name
        rules:
        - symbolizers:
          - polygon:
              stroke-color: '#777777'
              stroke-width: 0.5
              stroke-dasharray: '4 4'
              fill-color: '#EFEFC3'
              fill-opacity: 0.5
          - text:
              label: ${strToUpperCase(NAME)}
              font-size: 14
              font-family: SansSerif
              font-weight: bold
              fill-color: '#333333'
              x-autoWrap: 100
              x-maxDisplacement: 200
              x-goodnessOfFit: 0.8
              x-labelPriority: ${10-LABELRANK}

With these additions, the labels now appear much clearer:

     .. figure:: img/poly_label_styled.png

        Styled labels

Adding filters
--------------

Suppose we wish to display different colors for each country. The countries layer contains an attribute called ``MAPCOLOR7``, which assigns each country a number from 1 to 7, such that no adjacent countries have the same number. We can use this attribute to control what color a country is using :ref:`filters <cartography.ysld.reference.filters>`. Filters apply a condition to a rule, so that the symbolizers in that rule are only drawn if the filter evaluates to true.

#. Add seven rules, corresponding to the seven possibilities for ``MAPCOLOR7``. For each value, set the ``fill-color`` to the following values:

   .. list-table::
      :class: non-responsive
      :widths: 40 60 
      :header-rows: 1

      * - Filter
        - Parameter
      * - ``MAPCOLOR7 = 1``
        - ``fill-color: '#FFC3C3'``
      * - ``MAPCOLOR7 = 2``
        - ``fill-color: '#FFE3C3'``
      * - ``MAPCOLOR7 = 3``
        - ``fill-color: '#FFFFC3'``
      * - ``MAPCOLOR7 = 4``
        - ``fill-color: '#C3FFE3'``
      * - ``MAPCOLOR7 = 5``
        - ``fill-color: '#C3FFFF'``
      * - ``MAPCOLOR7 = 6``
        - ``fill-color: '#C3C3FF'``
      * - ``MAPCOLOR7 = 7``
        - ``fill-color: '#FFC3FF'``

#. After adding the filters, the style will look like:
   
   .. code-block:: yaml
      :emphasize-lines: 7-62

       name: countries
       title: Countries style
       abstract: countries of the world
       feature-styles:
       - name: name
         rules:
         - filter: ${MAPCOLOR7 = 1}
           symbolizers:
           - polygon:
               stroke-color: '#777777'
               stroke-dasharray: '4 4'
               stroke-width: 0.5
               fill-color: '#FFC3C3'
               fill-opacity: 0.5
         - filter: ${MAPCOLOR7 = 2}
           symbolizers:
           - polygon:
               stroke-color: '#777777'
               stroke-dasharray: '4 4'
               stroke-width: 0.5
               fill-color: '#FFE3C3'
               fill-opacity: 0.5
         - filter: ${MAPCOLOR7 = 3}
           symbolizers:
           - polygon:
               stroke-color: '#777777'
               stroke-dasharray: '4 4'
               stroke-width: 0.5
               fill-color: '#FFFFC3'
               fill-opacity: 0.5
         - filter: ${MAPCOLOR7 = 4}
           symbolizers:
           - polygon:
               stroke-color: '#777777'
               stroke-dasharray: '4 4'
               stroke-width: 0.5
               fill-color: '#C3FFE3'
               fill-opacity: 0.5
         - filter: ${MAPCOLOR7 = 5}
           symbolizers:
           - polygon:
               stroke-color: '#777777'
               stroke-dasharray: '4 4'
               stroke-width: 0.5
               fill-color: '#C3FFFF'
               fill-opacity: 0.5
         - filter: ${MAPCOLOR7 = 6}
           symbolizers:
           - polygon:
               stroke-color: '#777777'
               stroke-dasharray: '4 4'
               stroke-width: 0.5
               fill-color: '#C3C3FF'
               fill-opacity: 0.5
         - filter: ${MAPCOLOR7 = 7}
           symbolizers:
           - polygon:
               stroke-color: '#777777'
               stroke-dasharray: '4 4'
               stroke-width: 0.5
               fill-color: '#FFC3FF'
               fill-opacity: 0.5
         - symbolizers:
           - text:
               label: ${strToUpperCase(NAME)}
               font-size: 14
               font-family: SansSerif
               font-weight: bold
               fill-color: '#333333'
               x-autoWrap: 100
               x-maxDisplacement: 200
               x-goodnessOfFit: 0.8
               x-labelPriority: ${10-LABELRANK}

   .. figure:: img/poly_label_color.png

      Adjacent countries will not have the same color

Reducing redundant content using variables
--------------------------------------------

Much of the styling content in each of the filters used above is repeated. This repeated content can be reduced using :ref:`variables <cartography.ysld.reference.variables>`.

#. Add a directive block named ``polystyle`` to the beginning of the style:
   
   .. code-block:: yaml

      define: &polystyle
         stroke-color: '#777777'
         stroke-dasharray: '4 4'
         stroke-width: 0.5
         fill-opacity: 0.5

#. For each of the seven filter rules, remove every property except ``fill-color`` and add ``<<: *polystyle``. This will copy all the properties defined in our variable block to each of the rules. The style will now look like:

   .. code-block:: yaml
      :emphasize-lines: 1-5, 15, 20, 25, 30, 35, 40, 45
 
      define: &polystyle
        stroke-color: '#777777'
        stroke-dasharray: '4 4'
        stroke-width: 0.5
        fill-opacity: 0.5
      name: countries
      title: Countries style
      abstract: countries of the world
      feature-styles:
      - name: name
        rules:
        - filter: ${MAPCOLOR7 = 1}
          symbolizers:
          - polygon:
              <<: *polystyle
              fill-color: '#FFC3C3'
        - filter: ${MAPCOLOR7 = 2}
          symbolizers:
          - polygon:
              <<: *polystyle
              fill-color: '#FFE3C3'
        - filter: ${MAPCOLOR7 = 3}
          symbolizers:
          - polygon:
              <<: *polystyle
              fill-color: '#FFFFC3'
        - filter: ${MAPCOLOR7 = 4}
          symbolizers:
          - polygon:
              <<: *polystyle
              fill-color: '#C3FFE3'
        - filter: ${MAPCOLOR7 = 5}
          symbolizers:
          - polygon:
              <<: *polystyle
              fill-color: '#C3FFFF'
       - filter: ${MAPCOLOR7 = 6}
          symbolizers:
          - polygon:
              <<: *polystyle
              fill-color: '#C3C3FF'
        - filter: ${MAPCOLOR7 = 7}
          symbolizers:
          - polygon:
              <<: *polystyle
              fill-color: '#FFC3FF'
        - symbolizers:
          - text:
              label: ${strToUpperCase(NAME)}
              font-size: 14
              font-family: SansSerif
              font-weight: bold
              fill-color: '#333333'
              x-autoWrap: 100

Compacting thematic styles with transformation functions
--------------------------------------------------------

While filters are very useful, the required syntax is quite long, and much of the content is redundant. The exact same functionality can be accomplished much more concisely using the :ref:`recode function <cartography.ysld.reference.functions>`.

#. Replace the rules with our origional (before we added the filters):
   
   .. code-block:: yaml
      :emphasize-lines: 3-8
      
        rules:
           symbolizers:
           - polygon:
               stroke-color: '#777777'
               stroke-dasharray: '4 4'
               stroke-width: 0.5
               fill-color: '#EFEFC3'
               fill-opacity: 0.5

#. Change the ``fill-color`` to the following CQL expression:
   
   .. code-block:: yaml
      :emphasize-lines: 7-11
      
        rules:
           symbolizers:
           - polygon:
               stroke-color: '#777777'
               stroke-dasharray: '4 4'
               stroke-width: 0.5
               fill-color: ${
                   recode(MAPCOLOR7,
                     1, '#FFC3C3', 2, '#FFE3C3', 3, '#FFFFC3', 4, '#C3FFE3',
                     5, '#C3FFFF', 6, '#C3C3FF', 7, '#FFC3FF')
                 }
               fill-opacity: 0.5

   This sets the ``fill-color`` based on the value of ``MAPCOLOR7``, according to the key-value pairs in the ``recode`` function. If ``MAPCOLOR7 = 1``, set to ``'#FFC3C3'``, if ``MAPCOLOR7 = 2`` set to ``'#FFE3E3``, etc.

   It should be noted that this will produce the *exact same output* as in the previous section.

Final style
-----------

The full style now looks like this:

.. literalinclude:: files/ysldtut_poly.ysld
   :language: yaml

.. note:: :download:`Download the final polygon style <files/ysldtut_poly.ysld>`

Continue on to :ref:`cartography.ysld.tutorial.line`.
