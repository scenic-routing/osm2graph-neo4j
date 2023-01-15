package me.callsen.taylor.osm2graph_neo4j.geo.impl;

import java.util.Map;

import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import me.callsen.taylor.osm2graph_neo4j.data.GraphDbLoader;
import me.callsen.taylor.osm2graph_neo4j.geo.INodeShapeSource;
import me.callsen.taylor.scenicrouting.javasdk.RoutingConstants;

public class GraphNodeShapeSource implements INodeShapeSource {

  protected GraphDbLoader graphDbLoader;

  public GraphNodeShapeSource(GraphDbLoader graphDbLoader) throws Exception {
    this.graphDbLoader = graphDbLoader;
  }

  public String getNodeLonLatString(Long osmId) {

    String returnString = "POINT(0 0)"; 

    try ( Transaction tx = this.graphDbLoader.getTransaction() ) {
      
      // includes hint to use property index on osm_id
      String cypherString = String.format("MATCH (n:%s {%s:%s}) USING INDEX n:%s RETURN n.lon, n.lat",
          RoutingConstants.NodeLabels.INTERSECTION,
          RoutingConstants.GRAPH_PROPERTY_NAME_OSM_ID,
          osmId,
          RoutingConstants.GRAPH_INDEX_NAME_INTERSECTION_OSM_ID);
      Result result = tx.execute(cypherString);
      Map<String,Object> row = result.next();
      returnString = row.get("n.lon").toString() + " " + row.get("n.lat").toString();

      tx.close();
    } catch (Exception e) {
      System.out.println("failed to get lonLat string of osm id " + osmId); 
      e.printStackTrace();
    }

    return returnString;
  }

}