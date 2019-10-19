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
    String action = args[5]; // action defaults to "default" per pom file
    System.out.println("OSM To Graph (Neo4j) Initialized with following parameters: ");
    System.out.println("   osmFile: " + osmFilePath);
    System.out.println("   graphDb: " + graphDbPath);
    System.out.println("   action:  " + action);

    // Initialize GraphDB wrapper - facilitates loading of data into Neo4j Graph
    GraphDb graphDb = new GraphDb(graphDbPath);

    // Initialize OSM XML parser - parses XML using SAX event-driven style
    OsmSource osmSource = new OsmSource(osmFilePath);

    // execute activity based on selected action
    switch(action) { 
      case "default": 
        graphDb.truncateGraphNodes();
        graphDb.createNodeIdOsmIndex();
        osmSource.loadNodesIntoDb(graphDb);
        osmSource.loadWaysIntoGraph(graphDb);
        break; 
      case "loadnodes": 
        osmSource.loadNodesIntoDb(graphDb); 
        break; 
      case "loadways": 
        osmSource.loadWaysIntoGraph(graphDb); 
        break; 
      case "createnodeindex": 
        graphDb.createNodeIdOsmIndex(); 
        break;
      case "resetgraphdb": 
        graphDb.truncateGraphNodes();
        break; 
      default: 
        System.out.println("Unsupported action - please try again"); 
    }

    // Shutdown GraphDB
    graphDb.shutdown();

    System.out.println("Task complete");

  }

}