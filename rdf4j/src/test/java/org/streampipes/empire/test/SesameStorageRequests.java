package org.streampipes.empire.test;

import com.clarkparsia.empire.impl.RdfQuery;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.streampipes.model.InvocableSEPAElement;
import org.streampipes.model.impl.EventStream;
import org.streampipes.model.impl.graph.SecDescription;
import org.streampipes.model.impl.graph.SepDescription;
import org.streampipes.model.impl.graph.SepaDescription;
import org.streampipes.model.impl.staticproperty.StaticProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

public class SesameStorageRequests implements StorageRequests {

	private StorageManager manager;
	private EntityManager entityManager;
	
	public SesameStorageRequests()
	{
		manager = StorageManager.INSTANCE;
		entityManager = manager.getEntityManager();
	}
	
	//TODO: exception handling
	
	@Override
	public boolean storeSEP(SepDescription sep) {
		if (exists(sep)) return false;
		entityManager.persist(sep);
		return true;
	}

	@Override
	public boolean storeSEP(String jsonld) {
		SepDescription sep;
		try {
			sep = Transformer.fromJsonLd(SepDescription.class, jsonld);
			return storeSEP(sep);
		} catch (RDFParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedRDFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RepositoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean storeSEPA(SepaDescription sepa) {
		if (exists(sepa)) return false;
		entityManager.persist(sepa);
		return true;
	}

	@Override
	public boolean storeSEPA(String jsonld) {
		SepaDescription sepa;
		try {
			sepa = Transformer.fromJsonLd(SepaDescription.class, jsonld);
			return storeSEPA(sepa);
		} catch (RDFParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedRDFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RepositoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public SepDescription getSEPById(URI rdfId) {
		return entityManager.find(SepDescription.class, rdfId);
	}

	@Override
	public SepDescription getSEPById(String rdfId) throws URISyntaxException {
		return getSEPById(new URI(rdfId));
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<SepDescription> getAllSEPs() {
		Query query = entityManager.createQuery(QueryBuilder.buildListSEPQuery());
		query.setHint(RdfQuery.HINT_ENTITY_CLASS, SepDescription.class);
		return query.getResultList();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<SepaDescription> getAllSEPAs() {
		Query query = entityManager.createQuery(QueryBuilder.buildListSEPAQuery());
		query.setHint(RdfQuery.HINT_ENTITY_CLASS, SepaDescription.class);
		return query.getResultList();
	}

	@Override
	public boolean deleteSEP(SepDescription sep) {
		entityManager.remove(sep);
		
		return true;
	}

	@Override
	public boolean deleteSEP(String rdfId) {
		SepDescription sep = entityManager.find(SepDescription.class, rdfId);
		entityManager.remove(sep);
		return true;
	}

	@Override
	public boolean deleteSEPA(SepaDescription sepa) {
		entityManager.remove(sepa);
		return true;
	}

	@Override
	public boolean deleteSEPA(String rdfId) {
		SepaDescription sepa = entityManager.find(SepaDescription.class, rdfId);
		return deleteSEPA(sepa);
	}

	@Override
	public boolean exists(SepDescription sep) {
		SepDescription storedSEP = entityManager.find(SepDescription.class, sep.getRdfId());
		return storedSEP != null ? true : false;
	}

	@Override
	public boolean exists(SepaDescription sepa) {
		SepaDescription storedSEPA = entityManager.find(SepaDescription.class, sepa.getElementId());
		return storedSEPA != null ? true : false;
	}

	@Override
	public boolean exists(String rdfId) {
		SepaDescription storedSEPA = entityManager.find(SepaDescription.class, rdfId);
		return storedSEPA != null ? true : false;
	}

	@Override
	public boolean update(SepDescription sep) {
		return deleteSEP(sep) && storeSEP(sep);
	}

	@Override
	public boolean update(SepaDescription sepa) {
		return deleteSEPA(sepa.getElementId()) && storeSEPA(sepa);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<SepDescription> getSEPsByDomain(String domain) {
		Query query = entityManager.createQuery(QueryBuilder.buildSEPByDomainQuery(domain));
		query.setHint(RdfQuery.HINT_ENTITY_CLASS, SepDescription.class);
		System.out.println(query.toString());
		return query.getResultList();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<SepaDescription> getSEPAsByDomain(String domain) {
		Query query = entityManager.createQuery(QueryBuilder.buildSEPAByDomainQuery(domain));
		query.setHint(RdfQuery.HINT_ENTITY_CLASS, SepaDescription.class);
		System.out.println(query.toString());
		return query.getResultList();
	}

	@Override
	public SepaDescription getSEPAById(String rdfId) throws URISyntaxException {
		return getSEPAById(new URI(rdfId));
	}

	@Override
	public SepaDescription getSEPAById(URI rdfId) {
		return entityManager.find(SepaDescription.class, rdfId);
	}

	@Override
	public SecDescription getSECById(String rdfId) throws URISyntaxException {
		return getSECById(new URI(rdfId));
	}

	@Override
	public SecDescription getSECById(URI rdfId) {
		return entityManager.find(SecDescription.class, rdfId);
	}

	@Override
	public boolean exists(SecDescription sec) {
		SecDescription storedSEC = entityManager.find(SecDescription.class, sec.getRdfId());
		return storedSEC != null ? true : false;
	}

	@Override
	public boolean update(SecDescription sec) {
		return deleteSEC(sec) && storeSEC(sec);
		
	}

	@Override
	public boolean deleteSEC(SecDescription sec) {
		entityManager.remove(sec);
		return true;
	}

	@Override
	public boolean storeSEC(SecDescription sec) {
		if (exists(sec)) return false;
		entityManager.persist(sec);
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<SecDescription> getAllSECs() {
		Query query = entityManager.createQuery(QueryBuilder.buildListSECQuery());
		query.setHint(RdfQuery.HINT_ENTITY_CLASS, SecDescription.class);
		return query.getResultList();
	}

	@Override
	public StaticProperty getStaticPropertyById(String rdfId) {
		return entityManager.find(StaticProperty.class, URI.create(rdfId));
	}

	@Override
	public boolean storeInvocableSEPAElement(InvocableSEPAElement element) {
		entityManager.persist(element);
		return true;
	}

	@Override
	public EventStream getEventStreamById(String rdfId) {
		return entityManager.find(EventStream.class, URI.create(rdfId));
	}

	

}
