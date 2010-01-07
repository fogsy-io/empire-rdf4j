package com.clarkparsia.empire.impl;

import com.clarkparsia.empire.DataSourceException;
import com.clarkparsia.empire.SupportsTransactions;

import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;

/**
 * Title: DataSourceEntityTransaction<br/>
 * Description: Implementation of the JPA EntityTransaction interface for an RDF data source.<br/>
 * Company: Clark & Parsia, LLC. <http://clarkparsia.com><br/>
 * Created: Dec 14, 2009 11:12:45 AM<br/>
 *
 * @author Michael Grove <mike@clarkparsia.com><br/>
 */
public class DataSourceEntityTransaction implements EntityTransaction {
	/**
	 * Whether or not the transaction is currently active
	 */
	private boolean mIsActive;

	/**
	 * Sets whether or not the transaction can only be rolled back
	 */
	private boolean mRollbackOnly = false;

	/**
	 * The data source the transaction is being performed on
	 */
	private SupportsTransactions mDataSource;

	/**
	 * Create (but not open) a transaction for the specified data source
	 * @param theDataSource the data source that will have the transaction 
	 */
	public DataSourceEntityTransaction(final SupportsTransactions theDataSource) {
		mDataSource = theDataSource;
	}

	/**
	 * @inheritDoc
	 */
	public void begin() {
		assertInactive();

		mIsActive = true;

		try {
			mDataSource.begin();
		}
		catch (DataSourceException e) {
			throw new PersistenceException(e);
		}
	}

	/**
	 * @inheritDoc
	 */
	public void commit() {
		assertActive();

		if (getRollbackOnly()) {
			// TODO: is this the right behavior?
			throw new RollbackException("Transaction cannot be committed, it is marked as rollback only.");
		}

		try {
			mDataSource.commit();
			mIsActive = false;
		}
		catch (DataSourceException e) {
			throw new RollbackException(e);
		}
	}

	/**
	 * @inheritDoc
	 */
	public void rollback() {
		assertActive();

		try {
			mDataSource.rollback();
		}
		catch (DataSourceException e) {
			throw new PersistenceException(e);
		}
		finally {
			mIsActive = false;
		}
	}

	/**
	 * @inheritDoc
	 */
	public void setRollbackOnly() {
		assertActive();

		mRollbackOnly = true;
	}

	/**
	 * @inheritDoc
	 */
	public boolean getRollbackOnly() {
		assertActive();

		return mRollbackOnly;
	}

	/**
	 * @inheritDoc
	 */
	public boolean isActive() {
		return mIsActive;
	}

	/**
	 * Force there to be no active transaction.  If one is active, an IllegalStateException is thrown
	 * @throws IllegalStateException if there is an active transaction
	 */
	private void assertInactive() {
		if (isActive()) {
			throw new IllegalStateException("Transaction must be inactive in order to perform this operation.");
		}
	}

	/**
	 * Force there to be an active transaction.  If one is not active, an IllegalStateException is thrown
	 * @throws IllegalStateException if there is not an active transaction
	 */
	private void assertActive() {
		if (!isActive()) {
			throw new IllegalStateException("Transaction must be active in order to perform this operation.");
		}
	}
}
