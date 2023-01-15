package me.callsen.taylor.osm2graph_neo4j.data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.values.storable.CoordinateReferenceSystem;
import org.neo4j.values.storable.PointValue;
import org.neo4j.values.storable.Values;

import me.callsen.taylor.osm2graph_neo4j.geo.GeomUtil;
import me.callsen.taylor.scenicrouting.javasdk.RoutingConstants.NodeLabels;
import me.callsen.taylor.scenicrouting.javasdk.RoutingConstants.RelationshipTypes;
import me.callsen.taylor.scenicrouting.javasdk.data.GraphDb;

public class GraphDbLoader extends GraphDb {

  private Transaction sharedTransaction;
  
  // define number of transactions accepted before commiting (excluding index and truncate function which commit no matter what)
  private static final int SHARED_TRANSACTION_COMMIT_INTERVAL = 5000;
  private int sharedTransactionCount = 0;

  public GraphDbLoader(String graphDbPath) throws Exception {

    super(graphDbPath);

    // supply config options to reduce database size
    Map<String, String> configRaw = new HashMap<String, String>();
    configRaw.put("dbms.tx_log.rotation.retention_policy", "1 files");
    configRaw.put("dbms.tx_log.rotation.size", "1M");
    configRaw.put("dbms.tx_log.rotation.retention_policy","keep_none");
    
    // initialize shared transaction - bundles multiple transactions into single commit to improve performance
    this.sharedTransaction = this.getTransaction();

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
      if (openNewTransaction) this.sharedTransaction = this.getTransaction();
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

}