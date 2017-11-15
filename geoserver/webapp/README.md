# GeoServer Web Application

This module contains the GeoServer web application. It includes:

* **api** - api endpionts for use with client-side apps
* **api-composer** (*Deprecated*) - a backend api for use with [Composer](https://github.com/boundlessgeo/composer)
* **web** - the Boundless Server UI theme for geoserver and geowebcache.
* **web-app** - configuration for bundling the Boundless Server Web Application (**geoserver.war**).

*Note: for instructions on getting this running with Composer see the [Composer](https://github.com/boundlessgeo/composer) project page.*

## Building

Invoke ant to build the module:

    ant build

This will result in a deployable GeoServer war file.

## Developing

To run the backend import this module into your Java IDE and run
the `Start` class. By default the backend will start up on port 8080.

If you are using Intellij IDE and you get an error at startup, modify the run configuration and add `/web-app` to the working directory.

### Tips and Tricks

Ant build targets:

    Main targets:

     build  Builds project
     help   Print help
     serve  Runs GeoServer
