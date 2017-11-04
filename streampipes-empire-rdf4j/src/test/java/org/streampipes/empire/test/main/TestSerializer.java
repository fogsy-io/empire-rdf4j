package org.streampipes.empire.test.main;

import org.eclipse.rdf4j.model.Graph;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.streampipes.commons.Utils;
import org.streampipes.empire.pinto.MappingOptions;
import org.streampipes.empire.pinto.RDFMapper;
import org.streampipes.empire.test.HttpJsonParser;
import org.streampipes.empire.test.StorageManager;
import org.streampipes.model.impl.graph.SepaDescription;
import org.streampipes.model.transform.CustomAnnotationProvider;
import org.streampipes.model.vocabulary.SEPA;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

public class TestSerializer {

  public static void main(String[] args) throws IOException {
    String jsonld = HttpJsonParser.getContentFromUrl(URI.create("http://localhost:8090/sepa/textfilter"));

    InputStream stream = new ByteArrayInputStream(
            jsonld.getBytes(StandardCharsets.UTF_8));
    Model statements;
    statements = Rio.parse(stream, "", RDFFormat.JSONLD);
    SepaDescription description = RDFMapper
            .builder()
            .set(MappingOptions.IGNORE_PROPERTIES_WITHOUT_ANNOTATION, true)
            .set(MappingOptions.REQUIRE_IDS, true)
            .set(MappingOptions.USE_PROVIDED_CLASSES, new CustomAnnotationProvider())
            .build()
            .readValue(statements, SepaDescription.class, getResource(statements));
    System.out.println(description.getName());

    Graph graph = RDFMapper
            .builder()
            .set(MappingOptions.IGNORE_PROPERTIES_WITHOUT_ANNOTATION, true)
            .set(MappingOptions.REQUIRE_IDS, true)
            .set(MappingOptions.USE_PROVIDED_CLASSES, new CustomAnnotationProvider())
            .build()
            .writeValue(description);

    System.out.println(Utils.asString(graph));

    StorageManager.INSTANCE.getSesameStorage().storeSEPA(description);

    List<SepaDescription> sepas = StorageManager.INSTANCE.getSesameStorage().getAllSEPAs();
    System.out.println(sepas.size());
  }

  private static Resource getResource(Model statements) {
    Iterator<Statement> st = statements.iterator();

    while (st.hasNext()) {
      Statement s = st.next();
      if ((s.getPredicate().equals(RDF.TYPE))) {

        if (s.getObject()
                .stringValue()
                .equals(SEPA.SEMANTICEVENTPROCESSINGAGENT
                        .toSesameURI().toString())) {
          return s.getSubject();
        } else if (s
                .getObject()
                .stringValue()
                .equals(SEPA.SEMANTICEVENTPRODUCER
                        .toSesameURI().toString())) {
          return s.getSubject();
        } else if (s
                .getObject()
                .stringValue()
                .equals(SEPA.SEMANTICEVENTCONSUMER
                        .toSesameURI().toString())) {
          return s.getSubject();
        } else if (s
                .getObject()
                .stringValue()
                .equals(SEPA.SEPAINVOCATIONGRAPH
                        .toSesameURI().toString())) {
          return s.getSubject();
        } else if (s
                .getObject()
                .stringValue()
                .equals(SEPA.SECINVOCATIONGRAPH
                        .toSesameURI().toString())) {
          return s.getSubject();
        }

      }


    }
    return null;
  }
}