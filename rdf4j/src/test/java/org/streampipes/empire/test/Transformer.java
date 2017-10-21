package org.streampipes.empire.test;

import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.streampipes.model.transform.JsonLdTransformer;

import java.io.IOException;

public class Transformer {


	public static <T> T fromJsonLd(Class<T> destination, String jsonld) throws RDFParseException, UnsupportedRDFormatException, RepositoryException, IOException {
		return new JsonLdTransformer().fromJsonLd(jsonld, destination);
	}

}
