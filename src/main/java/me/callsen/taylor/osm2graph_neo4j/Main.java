package me.callsen.taylor.osm2graph_neo4j;

import java.io.File;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import me.callsen.taylor.osm2graph_neo4j.helper.OsmSource;

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

    // Initialize GraphDB
    GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase( new File( graphDbPath ) );
    System.out.println("graph db initialized: " + graphDb);

    // Load OSM file - initialze Sniffer objects
    OsmSource.loadNodes(osmFilePath, graphDb);
    
    // Shutdown GraphDB
    graphDb.shutdown();

  }

}