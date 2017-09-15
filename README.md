SourceGraph Viewer
----------------

This application is used to visualize in a graphical way various outputs generated by SonarQube Analyzer plugins.

For the time being, the viewer is only working with JAVA sources, and uses the **SonarQube Java plugin** to generate graphs.
When analyzing sources, the viewer produces:

* The Exploded Graph (EG) : The result of execution of the Symbolic Execution engine on **the first method** of the provided sources.
* The Control Flow Graph (CFG) : The CFG corresponding to the body of **the first method** of the provided sources
* Syntax Tree : The provided sources as it is parsed by the corresponding SonarQube analyzer

Usage
--------

To start the viewer (web app), you have two options:

* execute `main` method from `org.sonar.java.viewer.Viewer` class;
* build and run the web app using command line `mvn exec:java`.

Then, open your web browser and navigate to `http://localhost:9999`. Note that default port (`9999`) is currently hardcoded.

License
--------

Copyright 2008-2017 SonarSource.
Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
