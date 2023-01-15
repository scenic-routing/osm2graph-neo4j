import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.spatial.Point;

import me.callsen.taylor.osm2graph_neo4j.data.GraphDbLoader;
import me.callsen.taylor.osm2graph_neo4j.data.OsmSource;

@TestInstance(Lifecycle.PER_CLASS)
public class MainTest {
  
  @TempDir
  private static Path directory;

  private static GraphDbLoader graphDbLoader;

  private static OsmSource source;

  @BeforeAll
  public void initResources() throws Exception {

    ClassLoader classLoader = getClass().getClassLoader();
    source = new OsmSource(classLoader.getResource("xml/sf-potrero.osm").getFile());
    assertNotNull(source);
    graphDbLoader = new GraphDbLoader(directory.toFile().getAbsolutePath());
    assertNotNull(graphDbLoader);

    source.loadNodesIntoDb(graphDbLoader);
    source.loadWaysIntoGraph(graphDbLoader);
  }

  @AfterAll
  public void shutdownResources() {
    graphDbLoader.shutdown();
  }

  @Test
  public void testNodeCount() throws Exception {
    Transaction tx = graphDbLoader.getTransaction();
    Result result = tx.execute("MATCH (n) RETURN COUNT(DISTINCT(n)) AS total");
    while ( result.hasNext() ) {
      Map<String, Object> row = result.next();
      long count = (Long) row.get("total");
      // assertEquals(676, count);
      assertEquals(5368, count);
    }
    tx.close();
  }

  @Test
  public void testNodeProperties() throws Exception {
    Transaction tx = graphDbLoader.getTransaction();
    Result result = tx.execute("MATCH (n) WHERE n.osm_id=65354557 RETURN n");
    while ( result.hasNext() ) {
      Map<String, Object> row = result.next();
      Node node = (Node)row.get("n");
      
      // osm_id
      assertEquals(65354557, node.getProperty("osm_id"));
      // geom as Point
      Point nodeGeom = (Point) node.getProperty("geom");
      List<Double> coords = nodeGeom.getCoordinate().getCoordinate();
      assertEquals(-122.3964163, coords.get(0));
      assertEquals(37.7511897, coords.get(1));
      // geom_wkt
      assertEquals("POINT(-122.3964163 37.7511897)", node.getProperty("geom_wkt"));
      // properties
      assertEquals(1, node.getProperty("version"));
    }
    tx.close();
  }

  @Test
  public void testRelationshipCount() throws Exception {
    Transaction tx = graphDbLoader.getTransaction();
    Result result = tx.execute("MATCH ()-[r]-() RETURN COUNT(DISTINCT(r)) AS total");
    while ( result.hasNext() ) {
      Map<String, Object> row = result.next();
      long count = (Long) row.get("total");
      assertEquals(670, count);
    }
    tx.close();
  }

  @Test
  public void testRelationshipCreatedCount() throws Exception {
    Transaction tx = graphDbLoader.getTransaction();
    Result result = tx.execute("MATCH (a)-[r]-(b) WHERE a.osm_id=65354557 RETURN COUNT(DISTINCT(r)) AS total");
    while ( result.hasNext() ) {
      Map<String, Object> row = result.next();
      long count = (Long) row.get("total");
      assertEquals(4, count);
    }
    tx.close();
  }

  @Test
  public void testRelationshipProperties() throws Exception {
    Transaction tx = graphDbLoader.getTransaction();
    Result result = tx.execute("MATCH ()-[r]-() WHERE r.start_osm_id=65354557 AND r.end_osm_id=6916235511 return DISTINCT(r)");
    while ( result.hasNext() ) {
      Map<String, Object> row = result.next();
      Relationship rel = (Relationship)row.get("r");
      
      // osm_id
      assertEquals(8920510, rel.getProperty("osm_id"));
      // start_osm_id
      assertEquals(65354557l, rel.getProperty("start_osm_id"));
      // end_osm_id
      assertEquals(6916235511l, rel.getProperty("end_osm_id"));
      // geom as Point
      Point[] wayGeom = (Point[]) rel.getProperty("geom");
      List<Double> coords = wayGeom[0].getCoordinate().getCoordinate();
      assertEquals(-122.396416, coords.get(0));
      assertEquals(37.75119, coords.get(1));
      // way wky
      assertEquals("LINESTRING(-122.396416 37.75119,-122.39642 37.751305)", rel.getProperty("way"));
      // properties
      assertEquals("residential", rel.getProperty("highway"));
    }
    tx.close();
  }

}
