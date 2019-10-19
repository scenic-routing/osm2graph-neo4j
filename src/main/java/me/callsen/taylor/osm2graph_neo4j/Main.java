package me.callsen.taylor.osm2graph_neo4j;

import me.callsen.taylor.osm2graph_neo4j.data.GraphDb;
import me.callsen.taylor.osm2graph_neo4j.data.OsmSource;

public class Main {

	public static void main( String[] args ) throws Exception {
    
    // Parameters
    //  ensure required args are specified - otherwise exit
    if (args[1].equals("default") || args[3].equals("default")) {
      System.out.println("Required paramters not specified - exiting");
      System.exit(1);
    }
    String osmFilePath = args[1];
    String graphDbPath = args[3];
    System.out.println("OSM To Graph (Neo4j) Initialized with following parameters: ");
    System.out.println("   osmFile: " + osmFilePath);
    System.out.println("   graphDb: " + graphDbPath);

    // Initialize GraphDB wrapper - facilitates loading of data into Neo4j Graph
    GraphDb graphDb = new GraphDb(graphDbPath);

    // Initialize OSM XML parser - parses XML using SAX event-driven style
    OsmSource osmSource = new OsmSource(osmFilePath);

    graphDb.truncateGraphNodes();

    graphDb.createNodeIdOsmIndex();

    // Load OSM nodes and properties into graph
    osmSource.loadNodesIntoDb(graphDb);
    
    osmSource.loadWaysIntoGraph(graphDb);
    
    // Shutdown GraphDB
    graphDb.shutdown();

  }

}