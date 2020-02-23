package io.fogsy.empire.core.empire.ds.impl;

import io.fogsy.empire.core.empire.ds.Alias;
import io.fogsy.empire.core.empire.ds.DataSource;
import io.fogsy.empire.core.empire.ds.DataSourceFactory;
import io.fogsy.empire.core.empire.ds.DataSourceException;
import io.fogsy.empire.core.empire.impl.sparql.SPARQLDialect;
import io.fogsy.empire.core.empire.impl.sparql.ARQSPARQLDialect;

import java.util.Map;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * <p>DataSourceFactory implementation to create a Sparql endpoint backed data source.</p>
 *
 * @author Michael Grove
 * @version 0.6.5
 * @since 0.6.5
 * @see SparqlEndpointDataSource
 */
@Alias("sparql")
public class SparqlEndpointSourceFactory implements DataSourceFactory {

	/**
	 * Configuration map key for the URL of the sparql endpoint.
	 */
	public static final String KEY_URL = "url";

	/**
	 * Configuration parameter for specifying arq sparql dialect w/ its special bnode encoding, or the standard dialect
	 * for sparql.
	 */
	public static final String KEY_DIALECT = "dialect";

	/**
	 * @inheritDoc
	 */
	public boolean canCreate(final Map<String, Object> theMap) {
		return theMap.containsKey(KEY_URL);
	}

	/**
	 * @inheritDoc
	 */
	public DataSource create(final Map<String, Object> theMap) throws DataSourceException {
		if (canCreate(theMap)) {
			try {
				SPARQLDialect aDialect = SPARQLDialect.instance();

				if (theMap.containsKey(KEY_DIALECT) && theMap.get(KEY_DIALECT).equals("arq")) {
					aDialect = ARQSPARQLDialect.instance();
				}

				return new SparqlEndpointDataSource(new URL(theMap.get(KEY_URL).toString()), aDialect);
			}
			catch (MalformedURLException e) {
				throw new DataSourceException(e);
			}
		}
		else {
			throw new DataSourceException("Invalid configuration map, missing required key '" + KEY_URL + "'.");
		}
	}
}
