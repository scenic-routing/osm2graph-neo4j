package me.callsen.taylor.osm2graph_neo4j;

import java.io.File;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

public class Main {
    
	public static void main( String[] args ) throws Exception {
    
    // ensure required args are specified - otherwise exit
    if (args[1].equals("default") || args[3].equals("default")) {
      System.out.println("Required paramters not specified - exiting");
      System.exit(1);
    }

    System.out.println("Main.class executed with args: " + args.length);
		
  }

}