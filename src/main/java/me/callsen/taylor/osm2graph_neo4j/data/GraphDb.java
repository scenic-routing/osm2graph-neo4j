package me.callsen.taylor.osm2graph_neo4j.data;

import java.io.File;

import org.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

public class GraphDb {

  private enum NodeLabels implements Label { INTERSECTION; } 
  private enum RelationshipTypes implements RelationshipType { CONNECTS; }
  
  public GraphDatabaseService db;
  private Transaction sharedTransaction;
  
  // define number of transactions accepted before commiting (excluding index and truncate function which commit no matter what)
  private static final int SHARED_TRANSACTION_COMMIT_INTERVAL = 5000;
  private int sharedTransactionCount = 0;

  public GraphDb(String graphDbPath) throws Exception {
    
    // initialize graph db connection
    db = new GraphDatabaseFactory().newEmbeddedDatabase( new File( graphDbPath ) );
    System.out.println("Graph DB @ " + graphDbPath + " initialized");
    
    // initialize shared transaction - bundles multiple transactions into single commit to improve performance
    this.sharedTransaction = this.db.beginTx();

  }

  // overloaded for convienence
  private void commitSharedTransaction() {
    this.commitSharedTransaction(true);
  }

  private void commitSharedTransaction(boolean openNewTransaction) {
    
    try {
      // commit pending transaction
      this.sharedTransaction.close();
      // reset transaction count to 0
      this.sharedTransactionCount = 0;
    } catch (Exception e) { 
      System.out.println("Warning - failed to commit shared transaction (not necessarily an issue)"); 
      e.printStackTrace();
    } finally {
      // open a new transaction, or unassign (used at app shutdown)
      if (openNewTransaction) this.sharedTransaction = this.db.beginTx();
      else this.sharedTransaction = null;
    }

  }

  public void createNode(JSONObject nodeJsonObject) {

    try {
    
      // use shared transaction if instantiated; otherwise create one	
      Node newIntersectionNode = this.db.createNode(NodeLabels.INTERSECTION);
      
      //apply properties to Node object
      for (String key : nodeJsonObject.keySet()) {
        newIntersectionNode.setProperty(key, nodeJsonObject.get(key));
      }
       
      // System.out.println("created intersection for node id " + nodeJsonObject.getLong("osm_id"));
    } catch (Exception e) { 
      System.out.println("FAILED to create intersection for node id " + nodeJsonObject.getInt("osm_id"));
      e.printStackTrace();
    } finally {
      this.sharedTransaction.success();
      // track amount of activity on shared transaction - commit to DB if interval reached
      this.sharedTransactionCount += 1;
      if (this.sharedTransactionCount > this.SHARED_TRANSACTION_COMMIT_INTERVAL) this.commitSharedTransaction();
    }
    
  }

  public void createRelationship(JSONObject wayJsonObject, long wayStartOsmId, long wayEndOsmId) {
  
    try {
      
      //retrieve start and stop nodes by osm_id (osm_Id within graph); create relationship between nodes that will correspond to road / way
      Node startNode = this.db.findNode( NodeLabels.INTERSECTION , "osm_id", wayStartOsmId );
      Node endNode = this.db.findNode( NodeLabels.INTERSECTION , "osm_id", wayEndOsmId );
      Relationship newRelationship = startNode.createRelationshipTo( endNode , RelationshipTypes.CONNECTS );
      
      //apply properties to newly created Relationship (representing a road / way)
      for (String key : wayJsonObject.keySet()) {
        newRelationship.setProperty(key, wayJsonObject.get(key));
      }
      
      // System.out.println("creating road relationship in Graph for node osm_ids " + wayStartOsmId + " and "+ wayEndOsmId + "; road relationship graph id " + newRelationship.getId() );
    } catch (Exception e) {
      System.out.println("FAILED to create road relationship in Graph for node osm_ids " + wayStartOsmId + " and "+ wayEndOsmId + "; road relationship id road id not available" ); 
      e.printStackTrace();
    } finally {
      this.sharedTransaction.success();
      // track amount of activity on shared transaction - commit to DB if interval reached
      //   - move at faster rate than nodes since ways contain much more data
      this.sharedTransactionCount += 500;
      if (this.sharedTransactionCount > this.SHARED_TRANSACTION_COMMIT_INTERVAL) this.commitSharedTransaction();
    }
    
  }

  public void shutdown(){
    
    // commit shared transaction
    this.commitSharedTransaction(false);

    // close db connection
    this.db.shutdown();
    
    System.out.println("Graph DB shutdown");

  }

  public void truncateGraphNodes() {
    
    System.out.println("truncating graph nodes..");
    
    try {
      
      this.db.execute( "MATCH (n) DETACH DELETE n" );
      
    } catch (Exception e) {
      System.out.println("failed to truncateGraph nodes"); 
      e.printStackTrace();
    } finally {
      this.sharedTransaction.success();
      this.commitSharedTransaction();
    }
    
  }
  
  public void truncateGraphRelationships() {
    
    System.out.println("truncating graph relationships..");
    
    try {
      
      this.db.execute( "MATCH (a)-[r]-(b) DETACH DELETE r" );

    } catch (Exception e) {
      System.out.println("failed to truncateGraph relationships"); 
      e.printStackTrace();
    } finally {
      this.sharedTransaction.success();
      this.commitSharedTransaction();
    }
    
  }
  
  public void createNodeIdOsmIndex() {
    
    System.out.println("creating node index for quick retrieval with osm_id");
    
    //create node to create index off of
    Node indexNode = null;
    try {
      indexNode = this.db.createNode(NodeLabels.INTERSECTION);
    } catch (Exception e) {
      System.out.println("failed to create index node!"); 
      e.printStackTrace();
    } finally {
      this.sharedTransaction.success();
      this.commitSharedTransaction();
    }
    
    //create index
    Transaction txB = null;
    try {
      this.db.execute( "CREATE INDEX ON :INTERSECTION(osm_id)" );
    } catch (Exception e) {
      System.out.println("failed to create index node!"); 
      e.printStackTrace();
    } finally {
      this.sharedTransaction.success();
      this.commitSharedTransaction();
    }
    
    //delete node that index was created with
    try {
      indexNode.delete();
    } catch (Exception e) {
      System.out.println("failed to create index node!"); 
      e.printStackTrace();
    } finally {
      this.sharedTransaction.success();
      this.commitSharedTransaction();
    }
    
  }
  
  public void dropNodeOsmIdIndex() {
    
    System.out.println("dropping node index for quick retrieval with osm_Id");
    
    //drop index if exists
    try {
      this.db.execute( "DROP INDEX ON :INTERSECTION(osm_id)" );
    } catch (Exception e) {
      System.out.println("warning - failed to drop osm_Id index; index may not exist (not necessarily an issue)");
    } finally {
      this.sharedTransaction.success();
      this.commitSharedTransaction();
    }
    
  }

}