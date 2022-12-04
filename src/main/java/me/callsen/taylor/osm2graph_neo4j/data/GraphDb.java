package me.callsen.taylor.osm2graph_neo4j.data;

import java.math.BigDecimal;
import java.nio.file.Paths;

import org.json.JSONObject;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

public class GraphDb {

  private enum NodeLabels implements Label { INTERSECTION; } 
  private enum RelationshipTypes implements RelationshipType { CONNECTS; }
  
  public GraphDatabaseService db;
  private DatabaseManagementService managementService;
  private Transaction sharedTransaction;
  
  // define number of transactions accepted before commiting (excluding index and truncate function which commit no matter what)
  private static final int SHARED_TRANSACTION_COMMIT_INTERVAL = 5000;
  private int sharedTransactionCount = 0;

  public GraphDb(String graphDbPath) throws Exception {
    
    // initialize graph db connection
    managementService = new DatabaseManagementServiceBuilder( Paths.get( graphDbPath ) ).build();
    db = managementService.database( DEFAULT_DATABASE_NAME );
    // db = new GraphDatabaseFactory().newEmbeddedDatabase( new File( graphDbPath ) );
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
      this.sharedTransaction.commit();
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
      Node newIntersectionNode = this.sharedTransaction.createNode(NodeLabels.INTERSECTION);
      
      //apply properties to Node object
      for (String key : nodeJsonObject.keySet()) {
        
        Object value = nodeJsonObject.get(key);

        // special fix for BigDecimal types, used fot lat/long e.g. -89.3837613
        if (value instanceof BigDecimal) {
          value = ((BigDecimal)value).floatValue();
        }

        newIntersectionNode.setProperty(key, value);
      }
       
      // System.out.println("created intersection for node id " + nodeJsonObject.getLong("osm_id"));
    } catch (Exception e) { 
      System.out.println("FAILED to create intersection for node id " + nodeJsonObject.getInt("osm_id"));
      e.printStackTrace();
    } finally {
      // track amount of activity on shared transaction - commit to DB if interval reached
      this.sharedTransactionCount += 1;
      if (this.sharedTransactionCount > SHARED_TRANSACTION_COMMIT_INTERVAL) this.commitSharedTransaction();
    }
    
  }

  public void createRelationship(JSONObject wayJsonObject, long wayStartOsmId, long wayEndOsmId) {
  
    try {
      
      //retrieve start and stop nodes by osm_id (osm_Id within graph); create relationship between nodes that will correspond to road / way
      Node startNode = this.sharedTransaction.findNode( NodeLabels.INTERSECTION , "osm_id", wayStartOsmId );
      Node endNode = this.sharedTransaction.findNode( NodeLabels.INTERSECTION , "osm_id", wayEndOsmId );
      Relationship newRelationship = startNode.createRelationshipTo( endNode , RelationshipTypes.CONNECTS );
      
      //apply properties to newly created Relationship (representing a road / way)
      for (String key : wayJsonObject.keySet()) {
        
        Object value = wayJsonObject.get(key);

        // special fix for BigDecimal types, used fot lat/long e.g. -89.3837613
        if (value instanceof BigDecimal) {
          value = ((BigDecimal)value).doubleValue();
        }
        newRelationship.setProperty(key, value);
      }

      // explicitly set start and end osm ids (useful for filtering cypher queries by direction)
      newRelationship.setProperty("start_osm_id", wayStartOsmId);
      newRelationship.setProperty("end_osm_id", wayEndOsmId);
      
      // System.out.println("creating road relationship in Graph for node osm_ids " + wayStartOsmId + " and "+ wayEndOsmId + "; road relationship graph id " + newRelationship.getId() );
    } catch (Exception e) {
      System.out.println("FAILED to create road relationship in Graph for node osm_ids " + wayStartOsmId + " and "+ wayEndOsmId + "; road relationship id road id not available" ); 
      e.printStackTrace();
    } finally {
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
    this.managementService.shutdown();
    
    System.out.println("Graph DB shutdown");

  }

  public void truncateGraphNodes() {
    
    System.out.println("truncating graph nodes..");
    
    try {
      
      this.sharedTransaction.execute( "MATCH (n) DETACH DELETE n" );
      
    } catch (Exception e) {
      System.out.println("failed to truncateGraph nodes"); 
      e.printStackTrace();
    } finally {
      this.commitSharedTransaction();
    }
    
  }
  
  public void truncateGraphRelationships() {
    
    System.out.println("truncating graph relationships..");
    
    try {
      
      this.sharedTransaction.execute( "MATCH (a)-[r]-(b) DETACH DELETE r" );

    } catch (Exception e) {
      System.out.println("failed to truncateGraph relationships"); 
      e.printStackTrace();
    } finally {
      this.commitSharedTransaction();
    }
    
  }
  
  public void createNodeIdOsmIndex() {
    
    System.out.println("creating node index for quick retrieval with osm_id");
    
    //create node to create index off of
    try {
      Node indexNode = this.sharedTransaction.createNode(NodeLabels.INTERSECTION);
      indexNode.setProperty("osm_id", "indexNode");
    } catch (Exception e) {
      System.out.println("failed to create index node!"); 
      e.printStackTrace();
    } finally {
      this.commitSharedTransaction();
    }
    
    //create index
    Transaction txB = null;
    try {
      this.sharedTransaction.execute( "CREATE INDEX ON :INTERSECTION(osm_id)" );
    } catch (Exception e) {
      System.out.println("failed to create index!"); 
      e.printStackTrace();
    } finally {
      this.commitSharedTransaction();
    }
    
    //delete node that index was created with
    try {
      Node indexNode = this.sharedTransaction.findNode( NodeLabels.INTERSECTION , "osm_id", "indexNode" );
      indexNode.delete();
    } catch (Exception e) {
      System.out.println("failed to delete index node!"); 
      e.printStackTrace();
    } finally {
      this.commitSharedTransaction();
    }
    
  }
  
  public void dropNodeOsmIdIndex() {
    
    System.out.println("dropping node index for quick retrieval with osm_Id");
    
    //drop index if exists
    try {
      this.sharedTransaction.execute( "DROP INDEX ON :INTERSECTION(osm_id)" );
    } catch (Exception e) {
      System.out.println("warning - failed to drop osm_Id index; index may not exist (not necessarily an issue)");
    } finally {
      this.commitSharedTransaction();
    }
    
  }

}