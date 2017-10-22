/*
 * Copyright (c) 2009-2010 Clark & Parsia, LLC. <http://www.clarkparsia.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.streampipes.empire.rdf4j;

import org.streampipes.empire.core.empire.config.ConfigKeys;
import org.streampipes.empire.core.empire.ds.Alias;
import org.streampipes.empire.core.empire.ds.DataSource;
import org.streampipes.empire.core.empire.ds.DataSourceException;
import org.streampipes.empire.core.empire.ds.DataSourceFactory;
import com.google.common.base.Splitter;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.util.RDFInserter;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.util.Map;

/**
 * <p>Implementation of the {@link DataSourceFactory} interface for creating Sesame 2.x Repository objects.</p>
 *
 * @author  Michael Grove
 * @since   0.6
 * @version 0.7
 */
@Alias(RepositoryDataSourceFactory.ALIAS)
public final class RepositoryDataSourceFactory implements DataSourceFactory, RepositoryFactoryKeys {
	/**
	 * @inheritDoc
	 */
    @Override
	public boolean canCreate(final Map<String, Object> theMap) {
		return true;
	}

	/**
	 * @inheritDoc
	 */
    @Override
	public DataSource create(final Map<String, Object> theMap) throws DataSourceException {
		if (!canCreate(theMap)) {
			throw new DataSourceException("Invalid configuration map: " + theMap);
		}

        Object aName = theMap.get(ConfigKeys.NAME);
		Object aURL = theMap.get(URL);
		Object aRepo = theMap.get(REPO);
		Object aFiles = theMap.get(FILES);
		Object aDir = theMap.get(DIR);
		Object aPhysRepo = theMap.get(REPO_HANDLE);

		Repository aRepository;

		try {
			if (aPhysRepo != null) {
				aRepository = (Repository) aPhysRepo;
			}
			else if (aURL != null && aRepo != null) {
				aRepository = new HTTPRepository(aURL.toString(), aRepo.toString());
				aRepository.initialize();
			}
			else if (aFiles != null) {
				aRepository = new SailRepository(new MemoryStore());

				try {
					aRepository.initialize();
				
					RepositoryConnection aConn = null;

                    try {
                        aConn = aRepository.getConnection();
                        aConn.begin();

                        for (String aFile : Splitter.on(',').omitEmptyStrings().trimResults().split(aFiles.toString())) {
                            RDFParser aParser = Rio.createParser(Rio.getParserFormatForFileName(aFile).orElse(null));

                            aParser.setRDFHandler(new RDFInserter(aConn));

                            if (isURL(aFile)) {
                                aParser.parse(new java.net.URL(aFile).openStream(), "");
                            }
                            else {
                                aParser.parse(new FileInputStream(aFile), "");
                            }
                        }

                        aConn.commit();
                    }
                    finally {
                        if (aConn != null) {
                            aConn.close();
                        }
                    }
                }
				catch (Exception e) {
					throw new DataSourceException(e);
				}
			}
			else if (aDir != null) {
				aRepository = new SailRepository(new MemoryStore(new File(aDir.toString())));

				aRepository.initialize();
			}
			else {
				aRepository = new SailRepository(new MemoryStore());
				aRepository.initialize();
			}

			return new RepositoryDataSource(aRepository, theMap.containsKey(QUERY_LANG) && theMap.get(QUERY_LANG).toString().equalsIgnoreCase(LANG_SERQL));
		}
		catch (RepositoryException e) {
			throw new DataSourceException(e);
		}
	}

	private static boolean isURL(final String theURL) {
		try {
			new java.net.URL(theURL);
			return true;
		}
		catch (MalformedURLException theE) {
			return false;
		}
	}
}
