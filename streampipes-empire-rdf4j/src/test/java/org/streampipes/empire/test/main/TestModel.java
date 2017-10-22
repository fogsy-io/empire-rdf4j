package org.streampipes.empire.test.main;

import org.streampipes.empire.test.HttpJsonParser;
import org.streampipes.empire.test.StorageManager;
import org.streampipes.model.impl.graph.SepaDescription;
import org.streampipes.model.transform.JsonLdTransformer;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestModel {

  public static void main(String[] args) throws IOException, InterruptedException {

    List<String> epas = Arrays.asList("textfilter", "movement", "numericalfilter", "and", "eventrate", "count",
            "topX", "compose", "distribution", "observenumerical");

    List<URI> epaUris = new ArrayList<>();

    for (String epa : epas) {
      epaUris.add(URI.create("http://ipe-koi04.fzi.de:8090/sepa/" + epa));
    }

    for (int i = 0; i < 5; i++) {
      System.out.println("Starting run " + i);
      long start = System.currentTimeMillis();
      System.out.println("checking exist");
      long existStart = System.currentTimeMillis();
      for (String epaUri : epas) {
        if (StorageManager.INSTANCE.getSesameStorage().exists("http://pe-esper:8090/sepa/" + epaUri)) {
          StorageManager.INSTANCE
                  .getSesameStorage()
                  .deleteSEPA
                          ("http://pe-esper:8090/sepa/" + epaUri
                                  .toString());
        }
      }
      long existEnd = System.currentTimeMillis();
      System.out.println("exist took: " +(existEnd - existStart));

      System.out.println("Storing");

      long addStart = System.currentTimeMillis();
      for (URI epaUri : epaUris) {
        String jsonld = HttpJsonParser.getContentFromUrl(epaUri);

        SepaDescription sepaDescription = new JsonLdTransformer().fromJsonLd(jsonld, SepaDescription.class);
        StorageManager.INSTANCE.getSesameStorage().storeSEPA(sepaDescription);
      }
      long addEnd = System.currentTimeMillis();
      System.out.println("add took: " +(addEnd - addStart));

      System.out.println("Updating");

      long updateStart = System.currentTimeMillis();
      for (URI epaUri : epaUris) {
        String jsonld = HttpJsonParser.getContentFromUrl(epaUri);

        SepaDescription sepaDescription = new JsonLdTransformer().fromJsonLd(jsonld, SepaDescription.class);
        StorageManager.INSTANCE.getSesameStorage().update(sepaDescription);
      }

      long updateEnd = System.currentTimeMillis();
      System.out.println("update took: " +(updateEnd - updateStart));

      long end = System.currentTimeMillis();

      System.out.println("Run (" + i + ") Took: " + (end - start));
    }
  }


}
