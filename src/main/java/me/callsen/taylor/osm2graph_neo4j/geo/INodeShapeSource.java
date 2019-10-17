package me.callsen.taylor.osm2graph_neo4j.geo;

public interface INodeShapeSource {

  public String getNodeLonLatString(Long osmId);

}