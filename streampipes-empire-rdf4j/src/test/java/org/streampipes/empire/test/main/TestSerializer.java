//package org.streampipes.empire.test.main;
//
//import static org.eclipse.rdf4j.rio.helpers.BasicWriterSettings.PRETTY_PRINT;
//
//import org.eclipse.rdf4j.model.Graph;
//import org.eclipse.rdf4j.model.Model;
//import org.eclipse.rdf4j.model.Resource;
//import org.eclipse.rdf4j.model.Statement;
//import org.eclipse.rdf4j.model.vocabulary.RDF;
//import org.eclipse.rdf4j.rio.RDFFormat;
//import org.eclipse.rdf4j.rio.RDFHandlerException;
//import org.eclipse.rdf4j.rio.RDFWriter;
//import org.eclipse.rdf4j.rio.Rio;
//import org.eclipse.rdf4j.rio.helpers.JSONLDMode;
//import org.eclipse.rdf4j.rio.helpers.JSONLDSettings;
//import org.streampipes.empire.pinto.MappingOptions;
//import org.streampipes.empire.pinto.RDFMapper;
//import org.streampipes.empire.pinto.UriSerializationStrategy;
//import org.streampipes.empire.test.CustomAnnotationProvider;
//import org.streampipes.empire.test.HttpJsonParser;
//import org.streampipes.model.impl.graph.SepDescription;
//import org.streampipes.vocabulary.SEPA;
//
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.io.StringReader;
//import java.net.URI;
//import java.nio.charset.StandardCharsets;
//import java.util.Iterator;
//
//public class TestSerializer {
//
//  public static void main(String[] args) throws IOException {
//    String jsonld = HttpJsonParser.getContentFromUrl(URI.create("http://ipe-koi04.fzi.de:8089/sep/source_drillBit"));
//
//    InputStream stream = new ByteArrayInputStream(
//            jsonld.getBytes(StandardCharsets.UTF_8));
//    Model statements;
//    statements = Rio.parse(stream, "", RDFFormat.JSONLD);
//    SepDescription description = RDFMapper
//            .builder()
//            .set(MappingOptions.IGNORE_PROPERTIES_WITHOUT_ANNOTATION, true)
//            .set(MappingOptions.REQUIRE_IDS, true)
//            .set(MappingOptions.USE_PROVIDED_CLASSES, new CustomAnnotationProvider())
//            .set(MappingOptions.URI_SERIALIZATION_STRATEGY, UriSerializationStrategy.INSTANCE)
//            .build()
//            .readValue(statements, SepDescription.class, getResource(statements));
//    System.out.println(description.getName());
//
//    description.getEventStreams().forEach(desc -> {desc.setUri("http://ipe-koi04.fzi.de:8089/sep/" +desc.getUri());});
//
//    Graph graph = RDFMapper
//            .builder()
//            .set(MappingOptions.IGNORE_PROPERTIES_WITHOUT_ANNOTATION, true)
//            .set(MappingOptions.REQUIRE_IDS, true)
//            .set(MappingOptions.USE_PROVIDED_CLASSES, new CustomAnnotationProvider())
//            .set(MappingOptions.URI_SERIALIZATION_STRATEGY, UriSerializationStrategy.INSTANCE)
//            .build()
//            .writeValue(description);
//
//    System.out.println(asString(graph));
//
//   Model model2 = Rio.parse(new StringReader(asString(graph)), "", RDFFormat.JSONLD);
//
//    description = RDFMapper
//            .builder()
//            .set(MappingOptions.IGNORE_PROPERTIES_WITHOUT_ANNOTATION, true)
//            .set(MappingOptions.REQUIRE_IDS, true)
//            .set(MappingOptions.USE_PROVIDED_CLASSES, new CustomAnnotationProvider())
//            .set(MappingOptions.URI_SERIALIZATION_STRATEGY, UriSerializationStrategy.INSTANCE)
//            .build()
//            .readValue(model2, SepDescription.class, getResource(statements));
//    System.out.println(description.getName());
//
//
//    //StorageManager.INSTANCE.getSesameStorage().storeSEPA(description);
//
//    //List<SepaDescription> sepas = StorageManager.INSTANCE.getSesameStorage().getAllSEPAs();
//    //System.out.println(sepas.size());
//  }
//
//  public static String asString(Graph graph) {
//    OutputStream stream = new ByteArrayOutputStream();
//
//    RDFWriter writer = getRioWriter(stream);
//
//    Rio.write(graph, writer);
//    return stream.toString();
//  }
//
//  public static RDFWriter getRioWriter(OutputStream stream) throws RDFHandlerException
//  {
//    RDFWriter writer = Rio.createWriter(RDFFormat.JSONLD, stream);
//
//    writer.handleNamespace("sepa", "http://sepa.event-processing.org/sepa#");
//    writer.handleNamespace("ssn", "http://purl.oclc.org/NET/ssnx/ssn#");
//    writer.handleNamespace("xsd", "http://www.w3.org/2001/XMLSchema#");
//    writer.handleNamespace("empire", "urn:clarkparsia.com:empire:");
//    writer.handleNamespace("fzi", "urn:fzi.de:sepa:");
//
//    writer.getWriterConfig().set(JSONLDSettings.JSONLD_MODE, JSONLDMode.COMPACT);
//    writer.getWriterConfig().set(JSONLDSettings.OPTIMIZE, true);
//    writer.getWriterConfig().set(PRETTY_PRINT, true);
//
//    return writer;
//  }
//
//  private static Resource getResource(Model statements) {
//    Iterator<Statement> st = statements.iterator();
//
//    while (st.hasNext()) {
//      Statement s = st.next();
//      if ((s.getPredicate().equals(RDF.TYPE))) {
//
//        if (s.getObject()
//                .stringValue()
//                .equals(SEPA.SEMANTICEVENTPROCESSINGAGENT
//                        .toSesameURI().toString())) {
//          return s.getSubject();
//        } else if (s
//                .getObject()
//                .stringValue()
//                .equals(SEPA.SEMANTICEVENTPRODUCER
//                        .toSesameURI().toString())) {
//          return s.getSubject();
//        } else if (s
//                .getObject()
//                .stringValue()
//                .equals(SEPA.SEMANTICEVENTCONSUMER
//                        .toSesameURI().toString())) {
//          return s.getSubject();
//        } else if (s
//                .getObject()
//                .stringValue()
//                .equals(SEPA.SEPAINVOCATIONGRAPH
//                        .toSesameURI().toString())) {
//          return s.getSubject();
//        } else if (s
//                .getObject()
//                .stringValue()
//                .equals(SEPA.SECINVOCATIONGRAPH
//                        .toSesameURI().toString())) {
//          return s.getSubject();
//        }
//
//      }
//
//
//    }
//    return null;
//  }
//}