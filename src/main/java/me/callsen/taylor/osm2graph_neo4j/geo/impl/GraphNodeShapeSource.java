package me.callsen.taylor.osm2graph_neo4j.geo.impl;

import me.callsen.taylor.osm2graph_neo4j.data.GraphDb;
import me.callsen.taylor.osm2graph_neo4j.geo.INodeShapeSource;

import java.util.Map;
import org.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.Result;

public class GraphNodeShapeSource implements INodeShapeSource {

  protected GraphDatabaseService db;

	public GraphNodeShapeSource(GraphDb graphdb) throws Exception {
    this.db = graphdb.db;
  }

  public String getNodeLonLatString(Long osmId) {
    
    String returnString = "POINT(0 0)"; 

		Transaction tx = null;
		try {
			
			tx = this.db.beginTx();
			
      Result result = this.db.execute( "MATCH (n:INTERSECTION) WHERE n.osm_id = " + osmId + " RETURN n.lon, n.lat" );
      Map<String,Object> row = result.next();
      returnString = row.get("n.lon").toString() + " " + row.get("n.lat").toString();

			tx.success();
			
		} catch (Exception e) {
			System.out.println("feailed to get lonLat string of osm id " + osmId); 
			e.printStackTrace();
		} finally {
			tx.close();
    }
    
    return returnString;
		
	}

}