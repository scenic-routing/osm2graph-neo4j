package me.callsen.taylor.osm2graph_neo4j.helper;

import java.io.File;

import org.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

public class GraphWrapper {

	private enum NodeLabels implements Label { INTERSECTION; } 
	private enum RelationshipTypes implements RelationshipType { CONNECTS; }
	
	private GraphDatabaseService db;
	
	public GraphWrapper(String graphDbPath) throws Exception {

    db = new GraphDatabaseFactory().newEmbeddedDatabase( new File( graphDbPath ) );
    System.out.println("Graph DB @ " + graphDbPath + " initialized");

  }

  public void shutdown(){
    this.db.shutdown();
    System.out.println("Graph DB shutdown");
  }

  public void createIntersection(JSONObject nodeJsonObject) {
		
		Transaction tx = null;
		try {
		
			tx = this.db.beginTx();	
			Node newIntersectionNode = this.db.createNode(NodeLabels.INTERSECTION);
			
			//apply properties to Node object
			for (String key : nodeJsonObject.keySet()) {
				newIntersectionNode.setProperty(key, nodeJsonObject.get(key));
			}
			
			tx.success();
			System.out.println("creating intersection for node id " + nodeJsonObject.getLong("id"));
		} catch (Exception e) { 
			System.out.println("FAILED to create intersection for node id " + nodeJsonObject.getInt("id")); System.out.println(e.toString());
		} finally {
			tx.close();
		}
		
	}

}