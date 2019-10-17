package me.callsen.taylor.osm2graph_neo4j.geo;

import org.json.JSONObject;

import me.callsen.taylor.osm2graph_neo4j.geo.INodeShapeSource;

import org.json.JSONArray;

public class GeomUtil {

  public static void setWayGeometry( INodeShapeSource nodeShapeSource, JSONObject wayPropsObject , JSONArray wayNodesList , int wayStartIndex , int wayEndIndex ) {
    
    try {
    
      String lineString = "LINESTRING(";
        
        //assemble list of the osm node attrId refs for attachment to the way 
        //	so that this chunk of the way always know what original OSM nodes were a part of it, even if nodes are not full intersections
        JSONArray refOsmNodes = new JSONArray();
        
        //need to handle either forward or backward traversal through wayNodesList
        if ( wayStartIndex < wayEndIndex ) {
          for (int i=wayStartIndex; i<=wayEndIndex; i++) {
            long refNodeId = wayNodesList.getJSONObject(i).getLong("ref");
            lineString += nodeShapeSource.getNodeLonLatString(refNodeId) + ",";
            refOsmNodes.put(wayNodesList.getJSONObject(i));
          }
        } else if ( wayStartIndex > wayEndIndex ) {
          for (int i=wayStartIndex; i>=wayEndIndex; i--) {
            long refNodeId = wayNodesList.getJSONObject(i).getLong("ref");
            lineString += nodeShapeSource.getNodeLonLatString(refNodeId) + ",";
            refOsmNodes.put(wayNodesList.getJSONObject(i));
          }
        }
        
        lineString = lineString.substring(0, lineString.length()-1) + ")";
        wayPropsObject.put("way", lineString);
        
        //append list of osm node attrId refs
        wayPropsObject.put("refOsmNodes", refOsmNodes.toString());
        
        //utilized geotools library to compute length and bounds of road
        //LineString lineStringGeom = (LineString) App.reader.read(lineString);
        //wayPropsObject.put("length", lineStringGeom.getLength() * (Math.PI/180) * 6378137); //http://gis.stackexchange.com/questions/14449/java-vividsolutions-jts-wgs-84-distance-to-meters
    
    } catch (Exception e) { e.printStackTrace(); }
        
  }

}