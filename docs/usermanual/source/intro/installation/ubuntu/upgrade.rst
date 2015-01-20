.. _intro.installation.ubuntu.upgrade:

Upgrading to OpenGeo Suite Enterprise
=====================================

This section describes how to upgrade OpenGeo Suite to **OpenGeo Suite Enterprise** on Ubuntu.

.. note:: OpenGeo Suite Enterprise can only be obtained through `Boundless <http://boundlessgeo.com>`_. Please `contact us <http://boundlessgeo.com/about/contact-us/sales/>`__ for information on how to purchase OpenGeo Suite Enterprise.

#. To upgrade, you must first :ref:`uninstall <intro.installation.ubuntu.uninstall>` your current version of OpenGeo Suite. Your data and settings will be preserved.

#. Change to the ``root`` user:

   .. code-block:: bash

      sudo su - 

#. Remove the OpenGeo Suite repository entry, which is typically inside ``/etc/apt/sources.list.d/opengeo.list`` or ``/etc/apt/sources.list``. Open the file and delete the line or lines that refer to OpenGeo Suite.

#. Add the OpenGeo Suite Enterprise repository. Make sure to replace ``<username>`` and ``<password>`` with the user name and password supplied to you after your purchase.

   * If installing on Ubuntu 12:

     .. code-block:: bash

        echo "deb https://<username>:<password>@apt-ee.boundlessgeo.com/suite/v45/ubuntu/ precise main" > /etc/apt/sources.list.d/opengeo.list

   * If installing on Ubuntu 14:

     .. code-block:: bash

        echo "deb https://<username>:<password>@apt-ee.boundlessgeo.com/suite/v45/ubuntu/ trusty main" > /etc/apt/sources.list.d/opengeo.list

   .. note:: If you have OpenGeo Suite Enterprise and do not have a user name and password, please `contact us <http://boundlessgeo.com/about/contact-us/sales>`__.

#. Update the repository list:

   .. code-block:: bash

      apt-get update

#. Now install the appropriate OpenGeo Suite packages. You have options on what packages to install:

   .. note:: See the :ref:`Packages <intro.installation.ubuntu.packages>` section for details of individual packages.

   * To install typical server components:

     .. code-block:: bash

        apt-get install opengeo-server

   * To install typical client components:

     .. code-block:: bash

        apt-get install opengeo-client

   * To install typical client and server components:

     .. code-block:: bash

        apt-get install opengeo

#. If you installed any additional packages originally, you can update them now. For example:

   * To update the :ref:`Boundless SDK <webapps.sdk>`:

     .. code-block:: bash

        apt-get install opengeo-webapp-sdk

   * To update a GeoServer extension such as :ref:`WPS <processing>`:

        apt-get install geoserver-wps

After upgrade
-------------

The upgrade is now complete. Please see the section on :ref:`intro.installation.ubuntu.postinstall` to continue.
