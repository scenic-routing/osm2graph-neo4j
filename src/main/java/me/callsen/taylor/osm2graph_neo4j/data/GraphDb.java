package me.callsen.taylor.osm2graph_neo4j.data;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

import java.math.BigDecimal;
import java.nio.file.Paths;

import org.json.JSONObject;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.values.storable.CoordinateReferenceSystem;
import org.neo4j.values.storable.PointValue;
import org.neo4j.values.storable.Values;

import me.callsen.taylor.osm2graph_neo4j.geo.GeomUtil;

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

  public Transaction getTransaction() {
    return db.beginTx();
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
      
      // apply properties to Node object
      for (String key : nodeJsonObject.keySet()) {
        
        Object value = nodeJsonObject.get(key);

        // special fix for BigDecimal types, used fot lat/long e.g. -89.3837613
        if (value instanceof BigDecimal) {
          value = ((BigDecimal)value).floatValue();
        }

        newIntersectionNode.setProperty(key, value);
      }

      // add geom as WKT
      newIntersectionNode.setProperty("geom_wkt", "POINT(" + nodeJsonObject.getDouble("lon") + " " + nodeJsonObject.getDouble("lat") + ")");

      // add geom as Neo4j Point - https://neo4j.com/docs/graphql-manual/current/type-definitions/types/#type-definitions-types-point
      PointValue pointValue = Values.pointValue(CoordinateReferenceSystem.get(4326), nodeJsonObject.getDouble("lon"), nodeJsonObject.getDouble("lat"));
      newIntersectionNode.setProperty("geom", pointValue);

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

      // set relationship geometry as array of Neo4j Points - geometry read from "way" proprety set in GeomUtil.setWayGeometry()
      //  https://neo4j.com/docs/graphql-manual/current/type-definitions/types/#type-definitions-types-point
      //  https://github.com/neo4j/neo4j/blob/3.5/community/values/src/main/java/org/neo4j/values/storable/Values.java#L385
      LineString relationshipGeometry = GeomUtil.getLineStringFromWkt(wayJsonObject.getString("way"));
      PointValue[] relationshipPoints = new PointValue[relationshipGeometry.getCoordinates().length];
      for (int i = 0; i < relationshipGeometry.getCoordinates().length; ++i ) {
        Coordinate coord = relationshipGeometry.getCoordinateN(i);
        relationshipPoints[i] = Values.pointValue(CoordinateReferenceSystem.get(4326), coord.x, coord.y);
      }
      newRelationship.setProperty("geom", relationshipPoints);

      // System.out.println("creating road relationship in Graph for node osm_ids " + wayStartOsmId + " and "+ wayEndOsmId + "; road relationship graph id " + newRelationship.getId() );
    } catch (Exception e) {
      System.out.println("FAILED to create road relationship in Graph for node osm_ids " + wayStartOsmId + " and "+ wayEndOsmId + "; road relationship id road id not available" ); 
      e.printStackTrace();
    } finally {
      // track amount of activity on shared transaction - commit to DB if interval reached
      //   - move at faster rate than nodes since ways contain much more data
      this.sharedTransactionCount += 500;
      if (this.sharedTransactionCount > SHARED_TRANSACTION_COMMIT_INTERVAL) this.commitSharedTransaction();
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
  
  public void createNodeIndexes() {

    System.out.println("creating node index for quick retrieval with osm_id and geom");

    //create node to create index off of
    try {
      Node indexNode = this.sharedTransaction.createNode(NodeLabels.INTERSECTION);
      indexNode.setProperty("osm_id", "indexNode");
      indexNode.setProperty("geom", Values.pointValue(CoordinateReferenceSystem.get(4326), 50d, 50d));
    } catch (Exception e) {
      System.out.println("failed to create index node!"); 
      e.printStackTrace();
    } finally {
      this.commitSharedTransaction();
    }
    
    //create osm_id index
    try {
      this.sharedTransaction.execute( "CREATE INDEX ON :INTERSECTION(osm_id)" );
    } catch (Exception e) {
      System.out.println("failed to create index!"); 
      e.printStackTrace();
    } finally {
      this.commitSharedTransaction();
    }

    //create point index - https://neo4j.com/docs/cypher-manual/current/syntax/spatial/#spatial-values-point-index
    try {
      this.sharedTransaction.execute( "CREATE POINT INDEX intersection_point_idx FOR (n:INTERSECTION) ON (n.geom)" );
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

  public void createRelationshipIndexes() {

    System.out.println("creating relationship index for quick retrieval with geom");

    // create relationship to create index off of
    try {
      Node startNode = this.sharedTransaction.createNode(NodeLabels.INTERSECTION);
      Node endNode = this.sharedTransaction.createNode(NodeLabels.INTERSECTION);
      Relationship rel = startNode.createRelationshipTo(endNode, RelationshipTypes.CONNECTS);

      // used for lookup during delection
      startNode.setProperty("osm_id", "start");
      endNode.setProperty("osm_id", "end");
      rel.setProperty("osm_id", "rel");

      // set mock point array as geom
      PointValue[] points = new PointValue[2];
      points[0] = Values.pointValue(CoordinateReferenceSystem.get(4326), 50d, 50d);
      points[1] = Values.pointValue(CoordinateReferenceSystem.get(4326), 51d, 51d);
      rel.setProperty("geom", points);

    } catch (Exception e) {
      System.out.println("failed to create index relationship!");
      e.printStackTrace();
    } finally {
      this.commitSharedTransaction();
    }

    // create point index on geom - https://neo4j.com/docs/cypher-manual/current/syntax/spatial/#spatial-values-point-index
    try {
      this.sharedTransaction.execute( "CREATE POINT INDEX way_point_idx FOR ()-[r:CONNECTS]-() ON (r.geom)" );
    } catch (Exception e) {
      System.out.println("failed to create relationship index!");
      e.printStackTrace();
    } finally {
      this.commitSharedTransaction();
    }

    // delete relationship that index was created with
    try {
      Relationship rel = this.sharedTransaction.findRelationship( RelationshipTypes.CONNECTS , "osm_id", "rel" );
      rel.delete();
      Node startNode = this.sharedTransaction.findNode( NodeLabels.INTERSECTION , "osm_id", "start" );
      startNode.delete();
      Node endNode = this.sharedTransaction.findNode( NodeLabels.INTERSECTION , "osm_id", "end" );
      endNode.delete();
    } catch (Exception e) {
      System.out.println("failed to delete relationship index nodes/rel!"); 
      e.printStackTrace();
    } finally {
      this.commitSharedTransaction();
    }

  }
  
  public void dropNodeIndexes() {
    
    System.out.println("dropping node index for quick retrieval with osm_Id");
    
    //drop index if exists
    try {
      this.sharedTransaction.execute( "DROP INDEX ON :INTERSECTION(osm_id)" );
      this.sharedTransaction.execute( "DROP INDEX way_point_idx" );
    } catch (Exception e) {
      System.out.println("warning - failed to drop osm_Id index; index may not exist (not necessarily an issue)");
    } finally {
      this.commitSharedTransaction();
    }
    
  }

  public void dropRelationshipIndexes() {

    System.out.println("dropping relationship index for quick retrieval with geom");

    //drop index if exists
    try {
      this.sharedTransaction.execute( "DROP INDEX intersection_point_idx" );
    } catch (Exception e) {
      System.out.println("warning - failed to drop relationship geom index; index may not exist (not necessarily an issue)");
    } finally {
      this.commitSharedTransaction();
    }

  }

}