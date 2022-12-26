package me.callsen.taylor.osm2graph_neo4j;

import me.callsen.taylor.osm2graph_neo4j.data.GraphDb;
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
    GraphDb graphDb = new GraphDb(graphDbPath);

    // Initialize OSM XML parser - parses XML using SAX event-driven style
    OsmSource osmSource = new OsmSource(osmFilePath);

    // execute activity based on selected action
    switch(action) { 
      case "default":
        graphDb.dropNodeIndexes();
        graphDb.dropRelationshipIndexes();
        graphDb.createNodeIndexes();
        graphDb.createRelationshipIndexes();
        osmSource.loadNodesIntoDb(graphDb);
        osmSource.loadWaysIntoGraph(graphDb);
        break; 
      case "loadnodes": 
        osmSource.loadNodesIntoDb(graphDb); 
        break; 
      case "loadways": 
        osmSource.loadWaysIntoGraph(graphDb); 
        break; 
      case "createindexes":
        graphDb.dropNodeIndexes();
        graphDb.dropRelationshipIndexes();
        graphDb.createNodeIndexes();
        graphDb.createRelationshipIndexes();
        break;
      case "resetgraphdb": 
        graphDb.dropNodeIndexes();
        graphDb.dropRelationshipIndexes();
        graphDb.truncateGraphNodes();
        graphDb.truncateGraphRelationships();
        break; 
      default: 
        System.out.println("Unsupported action - please try again"); 
    }

    // Shutdown GraphDB
    graphDb.shutdown();

    System.out.println("Task complete");

  }

}