# osm2graph Loader (Neo4j)
Imports OpenStreetMap data into a Neo4j graph database. Produces a simple schema that includes nodes, ways, and all properties/tags contained in the source OSM data.

Designed to read OSM data in XML format. Uses SAX event-driven XML parsing to accomidate large XML files.

**Concerns**:
* Does not make use of any spatial indexing, or the Noe4j Spatial plugin. Geometries are stored in WKT format as properties on the created nodes and relationships.

## Schema
![alt text](https://taylor.callsen.me/wp-content/uploads/2019/10/tcallsen-osm2graph-schema-v1.jpg "Data model featuring graph nodes (OSM Nodes) and relationships (OSM Ways).")
OSM Nodes are imported as graph nodes with the label of `INTERSECTION`. Their point geometry stored in WKT format in the `geom` property.

OSM Ways are imported as relationships between the nodes (or intersections). Ways are labeled as `CONNECTS`, with their LineString WKT geometry stored in the `way` property. Only ways with a highway tag are imported (must match Xpath: `/osm/way[tag/@k = 'highway']`). More information about possible highway values is avilable [here](https://wiki.openstreetmap.org/wiki/Map_Features#Highway).

All other properties and tags in the source OSM data are flattened and attached as properties on the graph nodes/relationships that are created during import.

## Build
Built with the Java 8 JDK and Neo4j Server Community (version: 3.5.11). Code should be fairly portable, as no advanced features of Java or Neo4j are used.

This is a maven project and can be built using:
```
mvn clean install
```
Make sure the Neo4j library version in the `pom.xml` file matches the Neo4j Server version. Available versions in Maven Central can be found [here](https://mvnrepository.com/artifact/org.neo4j/neo4j).

## Running

The importer is executed through Maven and accepts 3 paramters:
1. **osmFile** - required - path to the source OSM XML file
2. **graphDb** - required - filesystem path to the Neo4j GraphDB (web/Bolt API not supported)
3. **action** - optional - allows execution of specific actions listed below (only needed in advanced scenarios)

To perform the default import:
```
mvn exec:java -DosmFile=/Users/Taylor/Downloads/SanFrancisco.osm -DgraphDb=/var/lib/neo4j/data/databases/graph.db 
```

To perform a specific action:
```
mvn exec:java -DosmFile=/Users/Taylor/Downloads/SanFrancisco.osm -DgraphDb=/var/lib/neo4j/data/databases/graph.db -Daction=loadnodes
```

A Bash script has been included (`run.sh`) to simplify execution and provide a menu for selecting actions. Just be sure to set the `OSMFILEPATH` and `GRAPHDBPATH` variables up top.

Available actions (all executed as part of defeault action):
* **loadnodes** - Loads OSM nodes into GraphDB
* **loadways** - Loads OSM ways into GraphDB
* **createnodeindex** - Creates an GraphDB index of Nodes on the `osm_id` property; used to speed up node lookup during way import
* **resetgraphdb** - Clears the GraphDB of all nodes, relationships, and indeces
 
## Sample Cypher Queries

Get street by name:
```
MATCH (a)-[r{name: 'Marlene-Dietrich-Straße'}]-(b) RETURN a, r, b
```
Get street by type:
```
MATCH (a)-[r]-(b) WHERE r.highway = 'secondary' OR r.highway = 'residential' RETURN a, r, b
```

## OSM Data Sources

City exports of OSM data are made avaiable via [BBBike](https://download.bbbike.org/osm/bbbike/).

Exports by country/region are available from [Geofabrik](http://download.geofabrik.de/).
