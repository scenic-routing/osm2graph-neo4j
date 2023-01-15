# osm2graph Loader (Java-Neo4j)
Imports OpenStreetMap data into a Neo4j graph database. Produces a simple schema that includes nodes, ways, and all properties/tags contained in the source OSM data. Geometries are stored as [Points](https://neo4j.com/docs/graphql-manual/current/type-definitions/types/#type-definitions-types-point) in Neo4j to support [geospatial queries](https://neo4j.com/docs/cypher-manual/current/functions/spatial/).

Designed to read OSM data in XML format. Uses SAX event-driven XML parsing to accomidate large XML files.

Uses the [scenic-routing/javasdk](https://github.com/scenic-routing/javasdk) to help with Neo4j Graph database interactions.

## Schema
![alt text](https://taylor.callsen.me/wp-content/uploads/2019/10/Tcallsen-Neo4j-graph-relationships.png "Data model featuring graph nodes (OSM Nodes) and relationships (OSM Ways).")

OSM Nodes are imported as graph nodes with the label of `INTERSECTION`. Their point geometry is stored as a [Point](https://neo4j.com/docs/graphql-manual/current/type-definitions/types/#type-definitions-types-point) in the `geom` property, and in WKT format in the `geom_wkt` property.

OSM Ways are imported as relationships between the nodes (or intersections). Ways are labeled as `CONNECTS`, with their LineString geometry being stored as an array of [Points](https://neo4j.com/docs/graphql-manual/current/type-definitions/types/#type-definitions-types-point) in the `geom` property, and a WKT string stored in the `way` property.

Only ways with a highway tag are imported (must match Xpath: `/osm/way[tag/@k = 'highway']`). More information about possible highway values is avilable [here](https://wiki.openstreetmap.org/wiki/Map_Features#Highway).

All other properties and tags in the source OSM data are flattened and attached as properties on the graph nodes/relationships that are created during import.

## Build

Built with the Java 11 JDK and Neo4j Server Community (version: 4.4.1). Code should be fairly portable, as no advanced features of Java or Neo4j are used.

This is a maven project and can be built using:
```
mvn clean install
```

Make sure the Neo4j library version in the `pom.xml` file matches the Neo4j Server version. Available versions in Maven Central can be found [here](https://mvnrepository.com/artifact/org.neo4j/neo4j).

## Running

The importer is executed via `java` CLI and accepts 3 paramters in this order:
1. **osmFile** - required - path to the source OSM XML file
2. **graphDb** - required - filesystem path to the Neo4j GraphDB (web/Bolt API not supported)
3. **action** - optional - allows execution of specific actions listed below (only needed in advanced scenarios)

To perform the default import:

```
java -jar target/osm2graph-neo4j-0.1.0-SNAPSHOT.jar /development/workspace/SanFrancisco.osm /development/workspace/neo4j/graph.db
```

To perform a specific action:

```
java -jar target/osm2graph-neo4j-0.1.0-SNAPSHOT.jar /development/workspace/SanFrancisco.osm /development/workspace/neo4j/graph.db loadnodes
```

Available actions (all executed as part of defeault action):
* **loadnodes** - Loads OSM nodes into GraphDB
* **loadways** - Loads OSM ways into GraphDB
* **createindexes** - Creates an GraphDB index of Nodes and Relationships on the `osm_id` property (Nodes only) and `geom` property; used to speed up node lookup during way import, and support [geospatial queries](https://neo4j.com/docs/cypher-manual/current/functions/spatial/)
* **resetgraphdb** - Clears the GraphDB of all nodes, relationships, and indexes.
 
## Sample Cypher Queries

Get street by name:

```
MATCH (a)-[r{name: 'Marlene-Dietrich-Straße'}]-(b) RETURN a, r, b
```

Get street by type:

```
MATCH (a)-[r]-(b) WHERE r.highway = 'secondary' OR r.highway = 'residential' RETURN a, r, b
```

Get intersection by bounding box:

```
WITH
  point({x: -89.36, y: 43.07, crs: "WGS-84"}) AS lowerLeft,
  point({x: -89.06, y: 43.57, crs: "WGS-84"}) AS upperRight
MATCH (n)
WHERE point.withinBBox(n.geom, lowerLeft, upperRight)
RETURN (n)
```

Get street by bounding box:

```
WITH
  point({x: -89.36, y: 43.07, crs: "WGS-84"}) AS lowerLeft,
  point({x: -89.06, y: 43.57, crs: "WGS-84"}) AS upperRight
MATCH (a)-[r]-(b)
WHERE
  point.withinBBox(r.geom[0], lowerLeft, upperRight) OR
  point.withinBBox(r.geom[1], lowerLeft, upperRight)
RETURN a, r, b
```

### Indexes

An index is created on the `osm_id` property of Nodes (used during import). A [Point Index](https://neo4j.com/docs/cypher-manual/current/syntax/spatial/#spatial-values-point-index) is created on the `geom` property of both Nodes and Relationships to support [geospatial queries](https://neo4j.com/docs/cypher-manual/current/functions/spatial/).

To view the available indexes, run the following Cypher query:

```
CALL db.indexes();
```

## OSM Data Sources

City exports of OSM data are made avaiable via [BBBike](https://download.bbbike.org/osm/bbbike/).

Exports by country/region are available from [Geofabrik](http://download.geofabrik.de/).

## Tests

JUnit tests can be executed with the following command:

```
mvn test
```

## More Information

Here is a [blog entry](https://taylor.callsen.me/loading-openstreetmap-data-into-a-graph-database/) I wrote about creating this loader, which goes into further detail on a few of the design decisions and methodologies used.

