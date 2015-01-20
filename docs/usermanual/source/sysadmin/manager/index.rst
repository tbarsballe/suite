.. _sysadmin.manager:

Setting up Tomcat Manager
=========================

.. note::

   This section only applies to the following versions of OpenGeo Suite:

   .. only:: basic

      * OpenGeo Suite for Ubuntu Linux
      * OpenGeo Suite for Red Hat Linux

   .. only:: enterprise

      * OpenGeo Suite for Ubuntu Linux
      * OpenGeo Suite for Red Hat Linux
      * OpenGeo Suite for Application Servers (when using **Tomcat**).

   OpenGeo Suite for Windows and OS X use `Jetty <http://www.eclipse.org/jetty/>`_, not Tomcat.

`Apache Tomcat <http://tomcat.apache.org>`_ is the application server used on Linux-based installations of OpenGeo Suite. While Apache Tomcat has a graphical manager for loading and managing web applications, **the Tomcat Manager is not loaded with OpenGeo Suite by default**.

This section will show you how to install the Tomcat Manager.

.. note:: The Tomcat service used by OpenGeo Suite is pulled in from standard repository sources, and is not specific to OpenGeo Suite. The same is true for the Tomcat Manager.

Installing via package manager
------------------------------

Ubuntu Linux
^^^^^^^^^^^^

The Tomcat Manager can be installed with the ``tomcat6-admin`` package through the standard Ubuntu package manager.

#. In a terminal, enter the following command:

   .. code-block:: console

      sudo apt-get install tomcat6-admin

#. Restart Tomcat:

   .. code-block:: console

      sudo service tomcat6 restart

#. To verify that the package was installed correctly, navigate to ``http://localhost:8080/manager`` (or substitute the location of the root web application URL) and you will see a request for credentials:

   .. figure:: img/tomcatcreds.png

      Tomcat manager asking for credentials

#. Click :guilabel:`Cancel` for now.

#. Continue below at the :ref:`sysadmin.manager.access` section.

Red Hat-based Linux
^^^^^^^^^^^^^^^^^^^

The Tomcat Manager can be installed with the ``tomcat-host-manager`` package through the standard Red Hat package manager.

#. In a terminal, enter the following command:

   .. code-block:: console

      sudo yum install tomcat-host-manager

#. Restart Tomcat:

   .. code-block:: console

      sudo service tomcat6 restart

#. To verify that the package was installed correctly, navigate to ``http://localhost:8080/manager`` (or substitute the location of the root web application URL) and you will see a request for credentials.

#. Click :guilabel:`Cancel` for now.

#. Continue below at the :ref:`sysadmin.manager.access` section.

.. _sysadmin.manager.access:

Allowing access to Tomcat Manager
---------------------------------

To access Tomcat Manager, you will need to create a user in Tomcat with the credentials to access it. The simplest way to create a user in Tomcat is as follows:

#. Open :file:`tomcat-users.xml` (typically found in :file:`/etc/tomcat6`) in a text editor.

#. In the ``<tomcat-users>`` block, add the following line:

   .. code-block:: xml

     <user username="admin" password="tomcat" roles="manager-gui"/> 

   This will create a user with the name of ``admin`` and a password of ``tomcat``. Feel free to substitute your own credentials.

   .. note:: Make sure this line is not inside a block that is commented out, otherwise it will have no effect. 

#. Save and close the file.

#. Restart Tomcat.

#. Navigate back to ``http://localhost:8080/manager`` and verify that the credentials allow access to Tomcat Manager.

   .. figure:: img/tomcatmanager.png

      Tomcat Manager

For more details on setting up a user in Tomcat, please see the `Tomcat Manager documentation <http://tomcat.apache.org/tomcat-6.0-doc/security-manager-howto.html>`_.
