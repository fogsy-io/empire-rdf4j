package com.clarkparsia.empire.impl;

import com.clarkparsia.empire.DataSource;
import com.clarkparsia.empire.QueryFactory;
import com.clarkparsia.empire.EmpireOptions;

import javax.persistence.Query;
import javax.persistence.NamedQuery;
import javax.persistence.QueryHint;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

/**
 * Title: AbstractQueryFactory <br/>
 * Description: Implements the common operations of a {@link QueryFactory} and defers query language specific operations
 * to concrete implementations of this class.<br/>
 * Company: Clark & Parsia, LLC. <http://clarkparsia.com><br/>
 * Created: Dec 14, 2009 4:04:47 PM<br/>
 *
 * @author Michael Grove <mike@clarkparsia.com><br/>
 */
public abstract class AbstractQueryFactory<T extends RdfQuery> implements QueryFactory {
	/**
	 * the data source the queries will be executed against
	 */
	private DataSource mSource;

	/**
	 * User-defined NamedQueries.  The actual queries are evaluated on-demand, we'll just keep the annotations which
	 * contain the information needed to create them here.
	 */
	private Map<String, NamedQuery> mNamedQueries = new HashMap<String, NamedQuery>();

	/**
	 * Create a new AbstractQueryFactory
	 * @param theSource the data source the queries will be executed against
	 */
	protected AbstractQueryFactory(final DataSource theSource) {
		mSource = theSource;

		Collection<Class> aClasses = EmpireOptions.ANNOTATION_PROVIDER.getClassesWithAnnotation(NamedQuery.class);
		for (Class aClass :  aClasses) {
			// wtf why do i need a cast here?
			NamedQuery aNamedQuery = (NamedQuery) aClass.getAnnotation(NamedQuery.class);

//			T aQuery = newQuery(aNamedQuery.query());
//			for (QueryHint aHint : aNamedQuery.hints()) {
//				aQuery.setHint(aHint.name(), aHint.value());
//			}

			addNamedQuery(aNamedQuery.name(), aNamedQuery);
		}
	}

	/**
	 * Create a new Query against the current data source with the given query string
	 * @param theQuery the query string
	 * @return a new query
	 */
	protected abstract T newQuery(String theQuery);

	/**
	 * Return the data source the queries will be executed against
	 * @return the data source
	 */
	protected DataSource getSource() {
		return mSource;
	}

	/**
	 * Add a query with the given name to this factory
	 * @param theName the name of the query
	 * @param theQuery the query identified by the given name
	 */
	private void addNamedQuery(final String theName, final NamedQuery theQuery) {
		mNamedQueries.put(theName, theQuery);
	}

	/**
	 * @inheritDoc
	 */
	public Query createQuery(final String theQueryString) {
		return newQuery(theQueryString);
	}

	/**
	 * @inheritDoc
	 */
	public Query createNamedQuery(final String theName) {
		if (mNamedQueries.containsKey(theName)) {
//			RdfQuery aQuery = mNamedQueries.get(theName);
			NamedQuery aNamedQuery = mNamedQueries.get(theName);

			T aQuery = newQuery(aNamedQuery.query());
			for (QueryHint aHint : aNamedQuery.hints()) {
				aQuery.setHint(aHint.name(), aHint.value());
			}

			aQuery.setSource(mSource);

			return aQuery;
		}
		else {
			throw new IllegalArgumentException("Query named '" + theName + "' does not exist.");
		}
	}

	/**
	 * @inheritDoc
	 */
	public Query createNativeQuery(final String theQueryString) {
		return newQuery(theQueryString);
	}

	/**
	 * @inheritDoc
	 */
	public Query createNativeQuery(final String theQueryString, final Class theResultClass) {
		T aQuery = newQuery(theQueryString);
		aQuery.setBeanClass(theResultClass);

		return aQuery;
	}

	/**
	 * @inheritDoc
	 */
	public Query createNativeQuery(final String theQueryString, final String theResultSetMapping) {
		// TODO: add support for this.  right now i dont know what the result set mapping does
		// so it's hard for me to implement.  I think it refers to javax.persistence.SqlResultSetMapping
		throw new UnsupportedOperationException();
	}
}
