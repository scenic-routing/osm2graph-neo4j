package me.callsen.taylor.osm2graph_neo4j;

import me.callsen.taylor.osm2graph_neo4j.data.GraphDbLoader;
import me.callsen.taylor.osm2graph_neo4j.data.OsmSource;

public class Main {

  public static void main( String[] args ) throws Exception {

    // Parameters
    //  ensure required args are specified - otherwise exit
    if (args.length < 2) {
      System.out.println("Required paramters not specified - exiting");
      System.exit(1);
    }
    String osmFilePath = args[0];
    String graphDbPath = args[1];
    String action = (args.length > 2) ? args[2] : "default"; // action defaults to "default" per pom file
    System.out.println("OSM To Graph (Neo4j) Initialized with following parameters: ");
    System.out.println("   osmFile: " + osmFilePath);
    System.out.println("   graphDb: " + graphDbPath);
    System.out.println("   action:  " + action);

    // Initialize GraphDB wrapper - facilitates loading of data into Neo4j Graph
    GraphDbLoader graphDbLoader = new GraphDbLoader(graphDbPath);

    // Initialize OSM XML parser - parses XML using SAX event-driven style
    OsmSource osmSource = new OsmSource(osmFilePath);

    // execute activity based on selected action
    switch(action) { 
      case "default":
        graphDbLoader.dropNodeIndexes();
        graphDbLoader.dropRelationshipIndexes();
        graphDbLoader.createNodeIndexes();
        graphDbLoader.createRelationshipIndexes();
        osmSource.loadNodesIntoDb(graphDbLoader);
        osmSource.loadWaysIntoGraph(graphDbLoader);
        break; 
      case "loadnodes": 
        osmSource.loadNodesIntoDb(graphDbLoader); 
        break; 
      case "loadways": 
        osmSource.loadWaysIntoGraph(graphDbLoader); 
        break; 
      case "createindexes":
        graphDbLoader.dropNodeIndexes();
        graphDbLoader.dropRelationshipIndexes();
        graphDbLoader.createNodeIndexes();
        graphDbLoader.createRelationshipIndexes();
        break;
      case "resetgraphdb": 
        graphDbLoader.dropNodeIndexes();
        graphDbLoader.dropRelationshipIndexes();
        graphDbLoader.truncateGraphNodes();
        graphDbLoader.truncateGraphRelationships();
        break; 
      default: 
        System.out.println("Unsupported action - please try again"); 
    }

    // Shutdown GraphDB
    graphDbLoader.shutdown();

    System.out.println("Task complete");

  }

}