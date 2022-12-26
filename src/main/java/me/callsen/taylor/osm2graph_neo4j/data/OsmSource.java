package me.callsen.taylor.osm2graph_neo4j.data;

import org.xml.sax.InputSource;
import jlibs.xml.DefaultNamespaceContext;
import jlibs.xml.sax.dog.NodeItem;
import jlibs.xml.sax.dog.XMLDog;
import jlibs.xml.sax.dog.expr.Expression;
import jlibs.xml.sax.dog.expr.InstantEvaluationListener;
import jlibs.xml.sax.dog.sniff.DOMBuilder;
import jlibs.xml.sax.dog.sniff.Event;

import me.callsen.taylor.osm2graph_neo4j.geo.GeomUtil;
import me.callsen.taylor.osm2graph_neo4j.geo.INodeShapeSource;
import me.callsen.taylor.osm2graph_neo4j.geo.impl.GraphNodeShapeSource;

import java.io.StringWriter;
import org.w3c.dom.Node;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.XML;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class OsmSource {

  protected InputSource osmInputSource;

  public OsmSource(String osmFilePath) {
    osmInputSource = new InputSource(osmFilePath);
  }

  public void loadNodesIntoDb(GraphDb graphDb) throws Exception {

    System.out.println("loading nodes into graph..");

    // configure xpath query
    XMLDog dog = new XMLDog( new DefaultNamespaceContext() );
    dog.addXPath("/osm/node");
    
    // configure sniffer and execute query
    Event event = dog.createEvent();
    event.setXMLBuilder(new DOMBuilder());
    
    // declare event callback for when xpath node is hit
    event.setListener(new InstantEvaluationListener(){
      
      // Initialize count of loaded nodes
      long nodeLoadedCount = 0;

      @Override
      public void onNodeHit(Expression expression, NodeItem nodeItem){
        
        // marshal XML node to JSON Object
        JSONObject rawNodeJsonObject = xmlNodeToJson( (Node)nodeItem.xml ).getJSONObject("node");

        // prepare osm item props for ingest into Neo4j (move id, flatten tags array)
        JSONObject nodeJsonObject = assembleOsmItemProps(rawNodeJsonObject);

        // write node to graph database; commit every 5000 nodes
        graphDb.createNode(nodeJsonObject);

        // output load progress
        ++nodeLoadedCount;
        if ( nodeLoadedCount % 5000 == 0) System.out.println("loaded " + nodeLoadedCount + " nodes..");

      }

      // not using these functions at the moment but must be overridden
      @Override
      public void finishedNodeSet(Expression expression){ }
      @Override
      public void onResult(Expression expression, Object result){ }

    });		
    
    // kick off dog sniffer
    dog.sniff(event, this.osmInputSource, false);

    System.out.println("finished loading nodes into graph");

  }

  public void loadWaysIntoGraph(GraphDb graphDb) throws Exception {
    
    System.out.println("loading ways into graph..");

    // configure xpath query - only include ways tagged as highways
    XMLDog dog = new XMLDog(new DefaultNamespaceContext());
    dog.addXPath("/osm/way[tag/@k = 'highway']");
    
    // configure sniffer and execute query
    Event event = dog.createEvent();
    event.setXMLBuilder(new DOMBuilder());
    
    // configure GraphNodeShapeSource to use a source for Node longitutate and latitute
    INodeShapeSource nodeShapeSource = new GraphNodeShapeSource(graphDb);

    // declare event callback for when xpath is hit
    event.setListener(new InstantEvaluationListener(){
      
      // Initialize count of loaded ways
      long wayLoadedCount = 0;
      
      @Override
      public void onNodeHit(Expression expression, NodeItem wayItem) {
          
        // marshal XML way to JSON Object
        JSONObject rawWayJsonObject = xmlNodeToJson( (Node)wayItem.xml ).getJSONObject("way");
        
        // retrieve list of nodes that comprise way
        JSONArray wayNodesList = rawWayJsonObject.optJSONArray("nd");
        if (wayNodesList == null) return; //skip if nodes not supplied or singular (we can't create a road here anyways)
        
        // assemble way properties object from any XML properties - shared/overwritten in all iterations of for loop below
        JSONObject wayPropsObject = assembleOsmItemProps(rawWayJsonObject);
        
        for (int nodeIndex = 1; nodeIndex < wayNodesList.length(); ++nodeIndex) {
          
          // create two-way way representation in graph since both directions are walkable
          //	- normally this is where one-way enforcement would take place
          
          long wayStarOsmId = wayNodesList.getJSONObject(nodeIndex).getLong("ref");
          long wayEndOsmId = wayNodesList.getJSONObject(nodeIndex - 1).getLong("ref");
          
          // forward
          
          GeomUtil.setWayGeometry(nodeShapeSource, wayPropsObject, wayNodesList, nodeIndex, nodeIndex - 1 );
          graphDb.createRelationship(wayPropsObject, wayStarOsmId, wayEndOsmId);
          
          // backward - flip the start and stop Nodes to create the same relationship in the other direction (Neo4j does not support bi-directional relationships)
          
          GeomUtil.setWayGeometry(nodeShapeSource, wayPropsObject, wayNodesList, nodeIndex - 1 , nodeIndex );
          graphDb.createRelationship(wayPropsObject, wayEndOsmId, wayStarOsmId);
        
        }

        // output load progress
        ++wayLoadedCount;
        if ( wayLoadedCount % 500 == 0) System.out.println("loaded " + wayLoadedCount + " ways..");

      }

      // not using these functions at the moment but must be overridden
      @Override
      public void finishedNodeSet(Expression expression){ }
      @Override
      public void onResult(Expression expression, Object result){ }

    });		
    
    //kick off dog sniffer
    dog.sniff(event, this.osmInputSource, false);

    System.out.println("finished loading ways into graph");

  }

  // surely taken from stackoverflow
  public static JSONObject xmlNodeToJson(Node node) {
    
    // convert XML node to string
    StringWriter sw = new StringWriter();
    Transformer xmlT;
    try {
      xmlT = TransformerFactory.newInstance().newTransformer();
      xmlT.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      xmlT.setOutputProperty(OutputKeys.INDENT, "yes");
      xmlT.transform(new DOMSource(node), new StreamResult(sw));
    } catch (TransformerException te) {
      System.out.println("error parsing Node XML to JSON");
    }
    String xmlNodeString = sw.toString();
    
    // parse string representation into JSONObject and return
    return XML.toJSONObject( xmlNodeString );
    
  }
  
  public static JSONObject assembleOsmItemProps(JSONObject osmItem) {
    
    // create props object based on props of original item
    JSONObject propsObject = new JSONObject(osmItem, JSONObject.getNames(osmItem));

    // tags - move from nested tag array and place as propreties directly on node 
    if (osmItem.has("tag")) {
      JSONArray tagsArray = osmItem.optJSONArray("tag");
      if (tagsArray != null) {
        // handle tags property as a JSONArray
        for (int i = 0; i < tagsArray.length(); i++) {
          JSONObject prop = tagsArray.getJSONObject(i);
          propsObject.put(prop.getString("k"), prop.get("v"));
        }
      } else {
        // if fails, handle tag property as a JSONObject
        JSONObject tagObject = osmItem.getJSONObject("tag");
        propsObject.put(tagObject.getString("k"), tagObject.get("v"));
      }
    }
    
    // move osm id to osm_id prop (so doesn't conflict with neo4j id)		
    propsObject.put("osm_id", osmItem.get("id"));
  
    // remove tags, id, and other non-needed props
    propsObject.remove("tag");
    propsObject.remove("id");
    propsObject.remove("nd");

    return propsObject;
    
  }

}