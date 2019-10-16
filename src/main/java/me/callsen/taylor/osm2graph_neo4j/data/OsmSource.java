package me.callsen.taylor.osm2graph_neo4j.data;

import org.xml.sax.InputSource;
import jlibs.xml.DefaultNamespaceContext;
import jlibs.xml.sax.dog.NodeItem;
import jlibs.xml.sax.dog.XMLDog;
import jlibs.xml.sax.dog.XPathResults;
import jlibs.xml.sax.dog.expr.Expression;
import jlibs.xml.sax.dog.expr.InstantEvaluationListener;
import jlibs.xml.sax.dog.sniff.DOMBuilder;
import jlibs.xml.sax.dog.sniff.Event;

import java.io.StringWriter;
import org.w3c.dom.Node;
import org.json.JSONObject;
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

    // configure xpath query
		XMLDog dog = new XMLDog( new DefaultNamespaceContext() );
		Expression xpath1 = dog.addXPath("/osm/node");
		
		// configure sniffer and execute query
		Event event = dog.createEvent();
		XPathResults results = new XPathResults(event);
		event.setXMLBuilder(new DOMBuilder());
		
		// declare event callback for when xpath node is hit
		event.setListener(new InstantEvaluationListener(){
      
      @Override
      public void onNodeHit(Expression expression, NodeItem nodeItem){
        
        // marshal XML node to JSON Object
        JSONObject nodeJsonObject = xmlNodeToJson( (Node)nodeItem.xml ).getJSONObject("node");

        // add geom field
        nodeJsonObject.put("geom", "POINT(" + nodeJsonObject.getDouble("lon") + " " + nodeJsonObject.getDouble("lat") + ")");

        // write node to graph database
        graphDb.createNode(nodeJsonObject);

      }

      // not usering these functions at the moment but must be overridden
      @Override
      public void finishedNodeSet(Expression expression){ }
      @Override
      public void onResult(Expression expression, Object result){ }

		});		
		
		//kick off dog sniffer
		dog.sniff(event, this.osmInputSource, false);

  }

  public static JSONObject xmlNodeToJson(Node node) {
		
		//convert XML node to string
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
		
		//parse string representation into JSONObject and return
		return XML.toJSONObject( xmlNodeString );
	}

}