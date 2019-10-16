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

    // Initialize GraphDB Wrapper - provides utility functions to facilitate loading of data
    GraphDb graphDb = new GraphDb(graphDbPath);

    // Load OSM nodes into graph - loads XML data using SAX event-driven parsing
    OsmSource.loadNodes(osmFilePath, graphDb);
    
    // Shutdown GraphDB
    graphDb.shutdown();

  }

}