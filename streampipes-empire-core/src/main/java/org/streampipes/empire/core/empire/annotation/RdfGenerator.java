/*
 * Copyright (c) 2009-2012 Clark & Parsia, LLC. <http://www.clarkparsia.com>
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

package org.streampipes.empire.core.empire.annotation;

import org.streampipes.empire.annotations.Namespaces;
import org.streampipes.empire.annotations.RdfId;
import org.streampipes.empire.annotations.RdfProperty;
import org.streampipes.empire.annotations.RdfsClass;
import org.streampipes.empire.core.empire.Dialect;
import org.streampipes.empire.core.empire.Empire;
import org.streampipes.empire.core.empire.EmpireGenerated;
import org.streampipes.empire.core.empire.EmpireOptions;
import org.streampipes.empire.core.empire.SupportsRdfId;
import org.streampipes.empire.core.empire.annotation.runtime.Proxy;
import org.streampipes.empire.core.empire.codegen.InstanceGenerator;
import org.streampipes.empire.core.empire.ds.DataSource;
import org.streampipes.empire.core.empire.ds.DataSourceException;
import org.streampipes.empire.core.empire.ds.DataSourceUtil;
import org.streampipes.empire.core.empire.ds.QueryException;
import org.streampipes.empire.core.empire.impl.serql.SerqlDialect;
import org.streampipes.empire.core.empire.util.BeanReflectUtil;
import org.streampipes.empire.cp.openrdf.utils.model.Models2;
import org.streampipes.empire.cp.openrdf.utils.util.ModelBuilder;
import com.google.common.collect.Maps;
import com.google.common.collect.ObjectArrays;
import com.google.common.collect.Sets;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.Statement;

import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Literals;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.eclipse.rdf4j.model.vocabulary.RDFS;

import java.lang.reflect.Type;

import java.util.Arrays;

import java.util.Date;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.List;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Modifier;

import java.util.Map;
import java.util.Locale;
import java.util.ArrayList;

import java.net.URISyntaxException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.streampipes.empire.core.empire.util.EmpireUtil;
import static org.streampipes.empire.core.empire.util.EmpireUtil.asPrimaryKey;
import static org.streampipes.empire.core.empire.util.EmpireUtil.isURI;

import org.streampipes.empire.cp.openrdf.utils.util.ResourceBuilder;
import org.streampipes.empire.cp.common.utils.util.PrefixMapping;
import org.streampipes.empire.cp.common.utils.base.Strings2;
import org.streampipes.empire.cp.common.utils.base.Dates;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Lists;
import com.google.inject.ProvisionException;
import com.google.inject.ConfigurationException;

import javax.persistence.Entity;
import javax.persistence.Transient;

import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyObject;
import javassist.util.proxy.MethodFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.WildcardTypeImpl;

/**
 * <p>Description: Utility for creating RDF from a compliant Java Bean, and for turning RDF (the results of a describe
 * on a given rdf:ID into a KB) into a Java bean.</p>
 * <p>Usage:<br/>
 * <code><pre>
 *   MyClass aObj = new MyClass();
 *
 *   // set some data on the object
 *   KB.add(RdfGenerator.toRdf(aObj));
 *
 *   MyClass aObjCopy = RdfGenerator.fromRdf(MyClass.class, aObj.getRdfId(), KB);
 *
 *   // this will print true
 *   System.out.println(aObj.equals(aObjCopy));
 * </pre>
 * </code>
 * </p>
 * <p>
 * Compliant classes must be annotated with the {@link Entity} JPA annotation, the {@link RdfsClass} annotation,
 * and must implement the {@link SupportsRdfId} interface.</p>
 *
 * @author	Michael Grove
 * @since	0.1
 * @version 1.0
 */
public final class RdfGenerator {

	/**
	 * Global ValueFactory to use for converting Java values into sesame objects for serialization to RDF
	 */
	private static final ValueFactory FACTORY = SimpleValueFactory.getInstance();

	private static final ContainsResourceValues CONTAINS_RESOURCES = new ContainsResourceValues();

	private static final LanguageFilter LANG_FILTER = new LanguageFilter(getLanguageForLocale());

	/**
	 * The logger
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(RdfGenerator.class.getName());

	/**
	 * Map from rdf:type URI's to the Java class which corresponds to that resource.
	 */
	private final static Multimap<IRI, Class> TYPE_TO_CLASS = HashMultimap.create();

	/**
	 * Map to keep a record of what instances are currently being created in order to prevent cycles.  Keys are the
	 * identifiers of the instances and the values are the instances
	 */
	public final static Map<Object, Object> OBJECT_M = Maps.newHashMap();

	private final static Set<Class<?>> REGISTERED_FOR_NS = Sets.newHashSet();

    /**
     * Cache the AccessibleObjects to avoid repeated inspections
     */
    private final static Map<Class<?>,Map<IRI,AccessibleObject>> ACCESSORS_BY_CLASS = Maps.newHashMap();

	/**
	 * Initialize some parameters in the RdfGenerator.  This caches namespace and type mapping information locally
	 * which will be used in subsequent rdf generation requests.
	 * @param theClasses the list of classes to be handled by the RdfGenerator
	 */
	public static synchronized void init(Collection<Class<?>> theClasses) {
		for (Class<?> aClass : theClasses) {
			RdfsClass aAnnotation = aClass.getAnnotation(RdfsClass.class);

			if (aAnnotation != null) {
				addNamespaces(aClass);

				TYPE_TO_CLASS.put(FACTORY.createIRI(PrefixMapping.GLOBAL.uri(aAnnotation.value())), aClass);
			}
		}
	}

	/**
	 * Create an instance of the specified class and instantiate it's data from the given data source using the RDF
	 * instance specified by the given URI
	 * @param theClass the class to create
	 * @param theKey the id of the RDF individual containing the data for the new instance
	 * @param theSource the KB to get the RDF data from
	 * @param <T> the type of the instance to create
	 * @return a new instance
	 * @throws InvalidRdfException thrown if the class does not support RDF JPA operations, or does not provide sufficient access to its fields/data.
	 * @throws DataSourceException thrown if there is an error while retrieving data from the graph
	 */
	public static <T> T fromRdf(Class<T> theClass, String theKey, DataSource theSource) throws InvalidRdfException, DataSourceException {
		return fromRdf(theClass, EmpireUtil.asPrimaryKey(theKey), theSource);
	}

	/**
	 * Create an instance of the specified class and instantiate it's data from the given data source using the RDF
	 * instance specified by the given URI
	 * @param theClass the class to create
	 * @param theURI the id of the RDF individual containing the data for the new instance
	 * @param theSource the KB to get the RDF data from
	 * @param <T> the type of the instance to create
	 * @return a new instance
	 * @throws InvalidRdfException thrown if the class does not support RDF JPA operations, or does not provide sufficient access to its fields/data.
	 * @throws DataSourceException thrown if there is an error while retrieving data from the graph
	 */
	public static <T> T fromRdf(Class<T> theClass, java.net.URI theURI, DataSource theSource) throws InvalidRdfException, DataSourceException {
		return fromRdf(theClass, new SupportsRdfId.URIKey(theURI), theSource);
	}

	/**
	 * Create an instance of the specified class and instantiate it's data from the given data source using the RDF
	 * instance specified by the given URI
	 * @param theClass the class to create
	 * @param theId the id of the RDF individual containing the data for the new instance
	 * @param theSource the KB to get the RDF data from
	 * @param <T> the type of the instance to create
	 * @return a new instance
	 * @throws InvalidRdfException thrown if the class does not support RDF JPA operations, or does not provide sufficient access to its fields/data.
	 * @throws DataSourceException thrown if there is an error while retrieving data from the graph
	 */
	public static <T> T fromRdf(Class<T> theClass, SupportsRdfId.RdfKey theId, DataSource theSource) throws InvalidRdfException, DataSourceException {
		T aObj;

		long start = System.currentTimeMillis();
		try {
			aObj = Empire.get().instance(theClass);
		}
		catch (ConfigurationException | ProvisionException ex) {
			aObj = null;
		}

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Tried to get instance of class in {} ms ", (System.currentTimeMillis()-start ));
		}

		start = System.currentTimeMillis();

		if (aObj == null) {
			// this means Guice construction failed, which is not surprising since that's not going to be the default.
			// so we'll try our own reflect based creation or create bytecode for an interface.

			try {
				long istart = System.currentTimeMillis();
				if (theClass.isInterface() || Modifier.isAbstract(theClass.getModifiers())) {
					aObj = InstanceGenerator.generateInstanceClass(theClass).newInstance();

					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("CodeGenerated instance in {} ms. ", (System.currentTimeMillis() - istart));
					}
				}
				else {
					aObj = theClass.newInstance();

					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("CodeGenerated instance in {} ms. ", (System.currentTimeMillis() - istart));
					}
				}
			}
			catch (InstantiationException e) {
				throw new InvalidRdfException("Cannot create instance of bean, should have a default constructor.", e);
			}
			catch (IllegalAccessException e) {
				throw new InvalidRdfException("Could not access default constructor for class: " + theClass, e);
			}
			catch (Exception e) {
				throw new InvalidRdfException("Cannot create an instance of bean", e);
			}

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Got reflect instance of class {} ms ",  (System.currentTimeMillis()-start ) );
			}

			start = System.currentTimeMillis();
		}

		asSupportsRdfId(aObj).setRdfId(theId);

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Has rdfId {} ms", (System.currentTimeMillis()-start ));
		}

		Class<T> aNewClass = determineClass(theClass, aObj, theSource);
		
		if (!aNewClass.equals(aObj.getClass())) {
			try {
	            aObj = aNewClass.newInstance();
            }
            catch (InstantiationException e) {
            	throw new InvalidRdfException("Cannot create instance of bean, should have a default constructor.", e);
            }
            catch (IllegalAccessException e) {
            	throw new InvalidRdfException("Could not access default constructor for class: " + theClass, e);
            }
			catch (Exception e) {
				throw new InvalidRdfException("Cannot create an instance of bean", e);
			}

			asSupportsRdfId(aObj).setRdfId(theId);
		}

		return fromRdf(aObj, theSource);
	}
	
	@SuppressWarnings("unchecked")
    private static <T> Class<T> determineClass(Class<T> theOrigClass, T theObj, DataSource theSource) throws InvalidRdfException, DataSourceException {
		Class aResult = theOrigClass;

//		ExtGraph aGraph = new ExtGraph(DataSourceUtil.describe(theSource, theObj));
		final Collection<Value> aTypes = DataSourceUtil.getValues(theSource, EmpireUtil.asResource(EmpireUtil.asSupportsRdfId(theObj)), RDF.TYPE);
		
		// right now, our best match is the original class (we will refine later)
		
//		final Resource aTmpRes = EmpireUtil.asResource(aTmpSupportsRdfId);
				
		// iterate for all rdf:type triples in the data
		// There may be multiple rdf:type triples, which can then translate onto multiple candidate Java classes
		// some of the Java classes may belong to the same class hierarchy, whereas others can have no common
		// super class (other than java.lang.Object)
		for (Value aValue : aTypes) {
			if (!(aValue instanceof IRI)) {
				// there is no URI in the object position of rdf:type
				// ignore that data
				continue;
			}
			
			IRI aType = (IRI) aValue;
						
			for (Class aCandidateClass : TYPE_TO_CLASS.get(aType)) {
				if (aCandidateClass.equals(aResult)) {
					// it is mapped to the same Java class, that we have; ignore
					continue;
				}

				// at this point we found an rdf:type triple that resolves to a different Java class than we have
				// we are only going to accept this candidate class if it is a subclass of the current Java class
				// (doing otherwise, may cause class cast exceptions)

				if (aResult.isAssignableFrom(aCandidateClass)) {
					aResult = aCandidateClass;
				}
			}
		}

		try {
			if (aResult.isInterface() || Modifier.isAbstract(aResult.getModifiers()) || !EmpireGenerated.class.isAssignableFrom(aResult)) {
				aResult = InstanceGenerator.generateInstanceClass(aResult);
			}
		}
		catch (Exception e) {
            throw new InvalidRdfException("Cannot generate a class for a bean", e);
		}
		
		return aResult;
	}

	/**
	 * Populate the fields of the current instance from the RDF indiviual with the given URI
	 * @param theObj the Java object to populate
	 * @param theSource the KB to get the RDF data from
	 * @param <T> the type of the class being populated
	 * @return theObj, populated from the specified DataSource
	 * @throws InvalidRdfException thrown if the object does not support the RDF JPA API.
	 * @throws DataSourceException thrown if there is an error retrieving data from the database
	 */
	@SuppressWarnings("unchecked")
	private synchronized static <T> T fromRdf(T theObj, DataSource theSource) throws InvalidRdfException, DataSourceException {
		final SupportsRdfId aTmpSupportsRdfId = asSupportsRdfId(theObj);
		final SupportsRdfId.RdfKey theKeyObj = aTmpSupportsRdfId.getRdfId();

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Converting {} to RDF.", theObj);
		}
		
		if (OBJECT_M.containsKey(theKeyObj)) {
			// TODO: this is probably a safe cast, i dont see how something w/ the same URI, which should be the same
			// object would change types
			return (T) OBJECT_M.get(theKeyObj);
		}

		try {

			OBJECT_M.put(theKeyObj, theObj);

			Model aGraph = DataSourceUtil.describe(theSource, theObj);

			if (aGraph.size() == 0) {
				return theObj;
			}

			final Resource aTmpRes = EmpireUtil.asResource(aTmpSupportsRdfId);
			Set<IRI> aProps = Sets.newHashSet();

			aGraph.filter(aTmpRes, null, null).stream()
			      .map(Statement::getPredicate).forEach(aProps::add);

			final SupportsRdfId aSupportsRdfId = asSupportsRdfId(theObj);
			
			final EmpireGenerated aEmpireGenerated = asEmpireGenerated(theObj);
			
			aEmpireGenerated.setAllTriples(aGraph);		
			
			OBJECT_M.put(theKeyObj, theObj);
			final Resource aRes = EmpireUtil.asResource(aSupportsRdfId);

			addNamespaces(theObj.getClass());

			Map<IRI, AccessibleObject> theCachedMap = ACCESSORS_BY_CLASS.get(theObj.getClass());

            if (theCachedMap == null) {
                theCachedMap = cacheAccessibles(theObj.getClass(), aRes);
            }
			Set<IRI> aUsedProps = Sets.newHashSet();

			for (IRI aProp : aProps) {
				AccessibleObject aAccess = theCachedMap.get(aProp);

				if (aAccess == null && RDF.TYPE.equals(aProp)) {
					// TODO: the following block should be entirely removed (leaving continue only)
					// right now, leaving it until the code review: code review before removing the following block
					
					// my understanding is that the following block was only necessary when having a support for a single-typed objects,
					// which is no longer the case 					
					
					// we can skip the rdf:type property.  it's basically assigned in the @RdfsClass annotation on the
					// java class, so we can figure it out later if need be. TODO: of course, if something has multiple types
					// that information is lost, which is not good.

					
					/*
					URI aType = (URI) aGraph.getValue(aRes, aProp);
					if (!TYPE_TO_CLASS.containsKey(aType) ||
						!TYPE_TO_CLASS.get(aType).isAssignableFrom(theObj.getClass())) {

						if (TYPE_TO_CLASS.containsKey(aType) && !TYPE_TO_CLASS.get(aType).getName().equals(theObj.getClass().getName())) {
							// TODO: this might just be an error
							LOGGER.warn("Asserted rdf:type of the individual does not match the rdf:type annotation on the object. " + aType + " " + TYPE_TO_CLASS.get(aType) + " " + theObj.getClass() + " " +TYPE_TO_CLASS.get(aType).isAssignableFrom(theObj.getClass())+ " " +TYPE_TO_CLASS.get(aType).equals(theObj.getClass()) + " " + TYPE_TO_CLASS.get(aType).getName().equals(theObj.getClass().getName()));
						}
						else {
							// if they're not equals() or isAssignableFrom, but have the same name, this is usually
							// means that the class loaders don't match.  so probably not an error, so no warning.
						}
					}
					*/

					continue;
				}
				else if (aAccess == null) {
					// this must be data that is not covered by the bean (perhaps accessible by a different view/bean for a differnent type of an individual)					
					continue;
				}

				aUsedProps.add(aProp);
				
				ToObjectFunction aFunc = new ToObjectFunction(theSource, aRes, aAccess, aProp);

				Object aValue = aFunc.apply(aGraph.filter(aRes, aProp, null).stream().map(Statement::getObject).collect(Collectors.toSet()));

				boolean aOldAccess = aAccess.isAccessible();

				try {
					BeanReflectUtil.setAccessible(aAccess, true);
					BeanReflectUtil.set(aAccess, theObj, aValue);
				}				
				catch (InvocationTargetException | IllegalAccessException e) {
					// oh crap
					throw new InvalidRdfException(e);
				}
				catch (IllegalArgumentException e) {
					// this is "likely" to happen.  we'll get this exception if the rdf does not match the java.  for example
					// if something is specified to be an int in the java class, but it typed as a float (though down conversion
					// in that case might work) the set call will fail.
					// TODO: shouldnt this be an error?

					LOGGER.warn("Probable type mismatch: {} {}", aValue, aAccess);
				}
				catch (RuntimeException e) {
					// TODO: i dont like keying on a RuntimeException here to get the error condition, but since the
					// Function interface does not throw anything, this is the best we can do.  maybe consider a
					// version of the Function interface that has a throws clause, it would make this more clear.

					// this was probably an error converting from a Value to an Object
					throw new InvalidRdfException(e);
				}
				finally {
					BeanReflectUtil.setAccessible(aAccess, aOldAccess);
				}
			}

			Model aInstanceTriples = Models2.newModel();

			aGraph.filter(aTmpRes, null, null)
			      .stream()
			      .filter(theStmt -> aUsedProps.contains(theStmt.getPredicate()))
			      .forEach(aInstanceTriples::add);
			
			aEmpireGenerated.setInstanceTriples(aInstanceTriples);

			return theObj;
		}
		finally {
			OBJECT_M.remove(theKeyObj);
		}
	}

    public static Map<IRI, AccessibleObject> cacheAccessibles( Class theClass, final Resource aRes ) {
        final Map<IRI, AccessibleObject> aAccessMap = Maps.newHashMap();

        Collection<Field> aFields = BeanReflectUtil.getAnnotatedFields( theClass );
        Collection<Method> aMethods = BeanReflectUtil.getAnnotatedSetters( theClass, true );

	    aFields.forEach(theField -> {
                if (theField.getAnnotation(RdfProperty.class) != null) {
                    IRI theURI = FACTORY.createIRI(PrefixMapping.GLOBAL.uri(theField.getAnnotation(RdfProperty.class).value()));
                    aAccessMap.put( theURI, theField );
                }
                else {
                    String aBase = "urn:empire:clark-parsia:";
                    if (aRes instanceof IRI) {
                        aBase = ((IRI)aRes).getNamespace();
                    }

                    aAccessMap.put(FACTORY.createIRI(aBase + theField.getName()),
                                   theField);
                }
            });

        aMethods.forEach(theMethod -> {
                RdfProperty aAnnotation = BeanReflectUtil.getAnnotation(theMethod, RdfProperty.class);
                if (aAnnotation != null) {
                    IRI theURI = FACTORY.createIRI( PrefixMapping.GLOBAL.uri( aAnnotation.value() ) );
                    aAccessMap.put( theURI, theMethod );
                }
            });

        ACCESSORS_BY_CLASS.put( theClass, aAccessMap );
        return aAccessMap;
    }

	/**
	 * Return the RdfClass annotation on the object.
	 * @param theObj the object to get that annotation from
	 * @return the objects' RdfClass annotation
	 * @throws InvalidRdfException thrown if the object does not have the required annotation, does not have an @Entity
	 * annotation, or does not {@link SupportsRdfId support Rdf Id's}
	 */
	private static RdfsClass asValidRdfClass(Object theObj) throws InvalidRdfException {
		if (!BeanReflectUtil.hasAnnotation(theObj.getClass(), RdfsClass.class)) {
			throw new InvalidRdfException("Specified value is not an RdfsClass object");
		}

		if (EmpireOptions.ENFORCE_ENTITY_ANNOTATION && !BeanReflectUtil.hasAnnotation(theObj.getClass(), Entity.class)) {
			throw new InvalidRdfException("Specified value is not a JPA Entity object");
		}

		// verify that it supports rdf id's
		asSupportsRdfId(theObj);

		return BeanReflectUtil.getAnnotation(theObj.getClass(), RdfsClass.class);
	}

	/**
	 * Return the object casted to {@link SupportsRdfId}
	 * @param theObj the object to cast
	 * @return the object, casted to the interface
	 * @throws InvalidRdfException thrown if the object does not implement the interface
	 */
	private static SupportsRdfId asSupportsRdfId(Object theObj) throws InvalidRdfException {
		if (!(theObj instanceof SupportsRdfId)) {
			throw new InvalidRdfException("Object of type '" + (theObj.getClass().getName()) + "' does not implements SupportsRdfId.");
		}
		else {
			return (SupportsRdfId) theObj;
		}
	}
	
	private static EmpireGenerated asEmpireGenerated(Object theObj) throws InvalidRdfException {
		if (!(theObj instanceof EmpireGenerated)) {
			throw new InvalidRdfException("Object of type '" + (theObj.getClass().getName()) + "' does not implements EmpireGenerated.");
		}
		else {
			return (EmpireGenerated) theObj;
		}
	}

	/**
	 * Given an object, return it's rdf:ID.  If it already has an id, that will be returned, otherwise the id
	 * will either be generated from the data, using the {@link RdfId} annotation as a guide, or it will auto-generate one.
	 * @param theObj the object
	 * @return the object's rdf:Id
	 * @throws InvalidRdfException thrown if the object does not support the minimum to create or retrieve an rdf:ID
	 * @see SupportsRdfId
	 */
	public static Resource id(Object theObj) throws InvalidRdfException {
		SupportsRdfId aSupport = asSupportsRdfId(theObj);

		if (aSupport.getRdfId() != null) {
			return EmpireUtil.asResource(aSupport);
		}

		Field aIdField = BeanReflectUtil.getIdField(theObj.getClass());

		String aValue = hash(Strings2.getRandomString(10));
		String aNS = RdfId.DEFAULT;

		IRI aURI = FACTORY.createIRI(aNS + aValue);

		if (aIdField != null && !aIdField.getAnnotation(RdfId.class).namespace().equals("")) {
			aNS = aIdField.getAnnotation(RdfId.class).namespace();
		}

		if (aIdField != null) {
			boolean aOldAccess = aIdField.isAccessible();
			aIdField.setAccessible(true);

			try {
				if (aIdField.get(theObj) == null) {
					throw new InvalidRdfException("id field must have a value");
				}

				Object aValObj = aIdField.get(theObj);

				aValue = Strings2.urlEncode(aValObj.toString());

				if (isURI(aValObj)) {
					try {
						aURI = FACTORY.createIRI(aValObj.toString());
					}
					catch (IllegalArgumentException e) {
						// sometimes sesame disagrees w/ Java about what a valid URI is.  so we'll have to try
						// and construct a URI from the possible fragment
						aURI = FACTORY.createIRI(aNS + aValue);
					}
				}
				else {
					//aValue = hash(aValObj);
					aURI = FACTORY.createIRI(aNS + aValue);
				}
			}
			catch (IllegalAccessException ex) {
				throw new InvalidRdfException(ex);
			}

			aIdField.setAccessible(aOldAccess);
		}

		aSupport.setRdfId(new SupportsRdfId.URIKey(java.net.URI.create(aURI.toString())));

		return aURI;
	}

	/**
	 * Scan the object for {@link Namespaces} annotations and add them to the current list of known namespaces
	 * @param theObj the object to scan.
	 */
	public static void addNamespaces(Class<?> theObj) {
		if (theObj == null || REGISTERED_FOR_NS.contains(theObj)) {
			return;
		}

		REGISTERED_FOR_NS.add(theObj);

		Namespaces aNS = BeanReflectUtil.getAnnotation(theObj, Namespaces.class);

		if (aNS == null) {
			return;
		}

		int aIndex = 0;
		while (aIndex+1 < aNS.value().length) {
			String aPrefix = aNS.value()[aIndex];
			String aURI = aNS.value()[aIndex+1];

			// TODO: maybe have a local version of this, this will add a global namespace, and could potentially
			// overwrite global things that use the same prefix but different uris, which would be bad
			PrefixMapping.GLOBAL.addMapping(aPrefix, aURI);
			aIndex += 2;
		}
	}

	/**
	 * Return the given Java bean as a set of RDF triples
	 * @param theObj the object
	 * @return the object represented as RDF triples
	 * @throws InvalidRdfException thrown if the object cannot be transformed into RDF.
	 */
	public static Model asRdf(final Object theObj) throws InvalidRdfException {
		if (theObj == null) {
			return null;
		}

		Object aObj = theObj;

		if (aObj instanceof ProxyHandler) {
			aObj = ((ProxyHandler)aObj).mProxy.value();
		}
		else {
			try {
				if (aObj.getClass().getDeclaredField("handler") != null) {
					Field aProxy =  aObj.getClass().getDeclaredField("handler");
					aObj = ((ProxyHandler)BeanReflectUtil.safeGet(aProxy, aObj)).mProxy.value();
				}
			}
			catch (InvocationTargetException e) {
				// this is probably an error, we know its a proxy object, but can't get the proxied object
				throw new InvalidRdfException("Could not access proxy object", e);
			}
			catch (NoSuchFieldException e) {
				// this is probably ok.
			}
		}

		RdfsClass aClass = asValidRdfClass(aObj);

		Resource aSubj = id(aObj);

		addNamespaces(aObj.getClass());

		ModelBuilder aBuilder = new ModelBuilder();

		Collection<AccessibleObject> aAccessors = Sets.newHashSet();
		aAccessors.addAll(BeanReflectUtil.getAnnotatedFields(aObj.getClass()));
		aAccessors.addAll(BeanReflectUtil.getAnnotatedGetters(aObj.getClass(), true));

		try {
			ResourceBuilder aRes = aBuilder.instance(aBuilder.getValueFactory().createIRI(PrefixMapping.GLOBAL.uri(aClass.value())),
													 aSubj);

			for (AccessibleObject aAccess : aAccessors) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Getting rdf for : {}", aAccess);
				}

				AsValueFunction aFunc = new AsValueFunction(aAccess);

				if (aAccess.isAnnotationPresent(Transient.class)
					|| (aAccess instanceof Field
						&& Modifier.isTransient( ((Field)aAccess).getModifiers() ))) {

					// transient fields or accessors with the Transient annotation do not get converted.
					continue;
				}

				RdfProperty aPropertyAnnotation = BeanReflectUtil.getAnnotation(aAccess, RdfProperty.class);
				String aBase = "urn:empire:clark-parsia:";
				if (aRes instanceof IRI) {
					aBase = ((IRI)aRes).getNamespace();
				}

				IRI aProperty = aPropertyAnnotation != null
								? aBuilder.getValueFactory().createIRI(PrefixMapping.GLOBAL.uri(aPropertyAnnotation.value()))
								: (aAccess instanceof Field ? aBuilder.getValueFactory().createIRI(aBase + ((Field)aAccess).getName()) : null);

				boolean aOldAccess = aAccess.isAccessible();
				BeanReflectUtil.setAccessible(aAccess, true);

				Object aValue = BeanReflectUtil.get(aAccess, aObj);

				BeanReflectUtil.setAccessible(aAccess, aOldAccess);

				if (aValue == null || aValue.toString().equals("")) {
					continue;
				}
				else if (Collection.class.isAssignableFrom(aValue.getClass())) {
					@SuppressWarnings("unchecked")
					List<Value> aValueList = asList(aAccess, (Collection<?>) Collection.class.cast(aValue));

					if (aValueList.isEmpty() || aPropertyAnnotation == null) {
						continue;
					}

					if (aPropertyAnnotation.isList()) {
						aRes.addProperty(aProperty, aValueList);
					}
					else {
						for (Value aVal : aValueList) {
							aRes.addProperty(aProperty, aVal);
						}
					}
				}
				else {
					aRes.addProperty(aProperty, aFunc.apply(aValue));
				}
			}
		}
		catch (IllegalAccessException | RuntimeException e) {
			throw new InvalidRdfException(e);
		}
		catch (InvocationTargetException e) {
			throw new InvalidRdfException("Cannot invoke method", e);
		}

		return aBuilder.model();
	}

	/**
	 * Transform a list of Java Objects into the corresponding RDF values
	 * @param theAccess the accessor for the value
	 * @param theCollection the collection to transform
	 * @return the collection as a list of RDF values
	 * @throws InvalidRdfException thrown if any of the values cannot be transformed
	 */
	private static List<Value> asList(AccessibleObject theAccess, Collection<?> theCollection) throws InvalidRdfException {
		try {
			return theCollection.stream().map(new AsValueFunction(theAccess)).collect(Collectors.toList());
		}
		catch (RuntimeException e) {
			throw new InvalidRdfException(e.getMessage());
		}
	}

	/**
	 * Return a base64 encoded md5 hash of the given object
	 * @param theObj the object to hash
	 * @return the hashed version of the object.
	 */
	private static String hash(Object theObj) {
		return Strings2.hex(Strings2.md5(theObj.toString()));
	}

	/**
	 * Javassist {@link MethodHandler} implementation for method proxying.
	 */
	private static class CollectionProxyHandler implements MethodHandler {

		/**
		 * The proxy object which wraps the instance being proxied.
		 */
		private CollectionProxy mProxy;

		/**
		 * Create a new ProxyHandler
		 * @param theProxy the proxy object
		 */
		private CollectionProxyHandler(final CollectionProxy theProxy) {
			mProxy = theProxy;
		}

		/**
		 * Delegates the methods to the Proxy
		 * @inheritDoc
		 */
		public Object invoke(final Object theThis, final Method theMethod, final Method theProxyMethod, final Object[] theArgs) throws Throwable {
			return theMethod.invoke(mProxy.value(), theArgs);
		}
	}

	private static class CollectionProxy {
		private Collection mCollection;
		private AccessibleObject mField;
		private Collection<Value> theList;
		private ValueToObject valueToObject;

		public CollectionProxy(final AccessibleObject theField, final Collection<Value> theTheList, final ValueToObject theValueToObject) {
			mField = theField;
			theList = theTheList;
			valueToObject = theValueToObject;
		}

		private void init() {
			Collection<Object> aValues = BeanReflectUtil.instantiateCollectionFromField(BeanReflectUtil.classFrom(mField));

			for (Value aValue : theList) {
				Object aListValue = valueToObject.apply(aValue);

				if (aListValue == null) {
					throw new RuntimeException("Error converting a list value.");
				}

				aValues.add(aListValue);
			}

			mCollection = aValues;
		}

		public Collection value() {
			if (mCollection == null) {
				init();
				theList.clear();

				theList = null;
				mField = null;
				valueToObject = null;
			}

			return mCollection;
		}
	}

	/**
	 * Enabling this seems to use more memory than per-object proxying (or none at all).  Is javassist leaking memory?
	 * Experimental option, not currently used.
	 */
	@Deprecated
	public static final boolean PROXY_COLLECTIONS = false;

	/**
	 * Implementation of the function interface to turn a Collection of RDF values into Java bean(s).
	 */
	private static class ToObjectFunction implements Function<Collection<Value>, Object> {
		/**
		 * Function to turn a single value into an object
		 */
		private ValueToObject valueToObject;

		/**
		 * Reference to the Type which the values will be assigned
		 */
		private AccessibleObject mField;

		public ToObjectFunction(final DataSource theSource, Resource theResource, final AccessibleObject theField, final IRI theProp) {
			valueToObject = new ValueToObject(theSource, theResource, theField, theProp);

			mField = theField;
		}

		public Object apply(final Collection<Value> theList) {
			if (theList == null || theList.isEmpty()) {
				return BeanReflectUtil.instantiateCollectionFromField(BeanReflectUtil.classFrom(mField));
			}
			if (Collection.class.isAssignableFrom(BeanReflectUtil.classFrom(mField))) {
				try {

					if (PROXY_COLLECTIONS && !BeanReflectUtil.isPrimitive(refineClass(mField, BeanReflectUtil.classFrom(mField), null, null))) {
						Object aColType = BeanReflectUtil.instantiateCollectionFromField(BeanReflectUtil.classFrom(mField));

						ProxyFactory aFactory = new ProxyFactory();
						aFactory.setInterfaces(aColType.getClass().getInterfaces());
						aFactory.setSuperclass(aColType.getClass());
						aFactory.setFilter(METHOD_FILTER);

						Object aResult = aFactory.createClass().newInstance();
						((ProxyObject) aResult).setHandler(new CollectionProxyHandler(new CollectionProxy(mField, theList, valueToObject)));
						return aResult;
					}
					else {
						Collection<Object> aValues = BeanReflectUtil.instantiateCollectionFromField(BeanReflectUtil.classFrom(mField));

						for (Value aValue : theList) {
							Object aListValue = valueToObject.apply(aValue);

							if (aListValue == null) {
								throw new RuntimeException("Error converting a list value.");
							}
							
							if (aListValue instanceof Collection) {
								aValues.addAll(((Collection) aListValue));
							}
							else {
								aValues.add(aListValue);
							}
						}

						return aValues;
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			/**
			 * if not list all literals
			 *   proceed
			 * else
			 *  if not lang aware
			 * 	 find non lang typed literals
			 *   if >= 1 non lang typed literals
			 *     proceed
			 *   else get language based on locale
			 *     find literals based on local lang
			 *
			 *   if == 0 literals
			 *     use original list
			 *   else use filtered list
			 *
			 * else if lang aware
			 */

			Collection<Value> aList = Sets.newHashSet(theList);

			if (!aList.stream().anyMatch(CONTAINS_RESOURCES)) {
				if (!EmpireOptions.ENABLE_LANG_AWARE) {
					Collection<Value> aLangFiltered = aList.stream().filter(theLit -> !Literals.isLanguageLiteral((Literal) theLit)).collect(Collectors.toList());

					if (aLangFiltered.isEmpty()) {
						LANG_FILTER.setLangCode(getLanguageForLocale());
						aLangFiltered = aList.stream().filter(LANG_FILTER).collect(Collectors.toList());
					}

					if (!aLangFiltered.isEmpty()) {
						aList = aLangFiltered;
					}
				}
				else {
					LANG_FILTER.setLangCode(mField.getAnnotation(RdfProperty.class).language());
					aList = aList.stream().filter(LANG_FILTER).collect(Collectors.toList());
				}
			}

			if (aList.isEmpty()) {
				// yes, we checked for emptiness to begin the method, but we might have done some filtering based on the
				// language tags, so we need to check again.
				return BeanReflectUtil.instantiateCollectionFromField(BeanReflectUtil.classFrom(mField));
			}
			else if ((aList.size() == 1) || (! EmpireOptions.STRICT_MODE)) {
				// collection of one element, just convert the single element and send that back
				return valueToObject.apply(aList.iterator().next());
			}
			else {
				throw new RuntimeException("Cannot convert list of values to anything meaningful for the field. " + mField + " " + aList);
			}
		}
	}

	private static String getLanguageForLocale() {
		return Locale.getDefault() == null || Locale.getDefault().toString().equals("")
			   ? "en"
			   : (Locale.getDefault().toString().indexOf("_") != -1
				  ? Locale.getDefault().toString().substring(0, Locale.getDefault().toString().indexOf("_"))
				  : Locale.getDefault().toString());
	}

	private static Class refineClass(final Object theAccessor, final Class theClass, final DataSource theSource, final Resource theId) {
		Class aClass = theClass;

		if (Collection.class.isAssignableFrom(aClass)) {
			// if the field we're assigning from is a collection, try and figure out the type of the thing
			// we're creating from the collection

			Type[] aTypes = null;

			if (theAccessor instanceof Field && ((Field)theAccessor).getGenericType() instanceof ParameterizedType) {
				aTypes = ((ParameterizedType) ((Field)theAccessor).getGenericType()).getActualTypeArguments();
			}
			else if (theAccessor instanceof Method) {
				aTypes = ((Method) theAccessor).getGenericParameterTypes();
			}

			if (aTypes != null && aTypes.length >= 1) {
				// first type argument to a collection is usually the one we care most about
				if (aTypes[0] instanceof ParameterizedType && ((ParameterizedType)aTypes[0]).getActualTypeArguments().length > 0) {
					Type aType = ((ParameterizedType)aTypes[0]).getActualTypeArguments()[0];

					if (aType instanceof Class) {
						aClass = (Class) aType;
					}
					else if (aType instanceof WildcardTypeImpl) {
						WildcardTypeImpl aWildcard = (WildcardTypeImpl) aType;
							// trying to suss out super v extends w/o resorting to string munging.
							if (aWildcard.getLowerBounds().length == 0 && aWildcard.getUpperBounds().length > 0) {
								// no lower bounds afaik indicates ? extends Foo
								aClass = ((Class)aWildcard.getUpperBounds()[0]);
							}
							else if (aWildcard.getLowerBounds().length > 0) {
								// lower & upper bounds I believe indicates something of the form Foo super Bar
								aClass = ((Class)aWildcard.getLowerBounds()[0]);
							}
							else {
								// shoot, we'll try the string hack that Adrian posted on the mailing list.
								try {
									aClass = Class.forName(aType.toString().split(" ")[2].substring(0, aTypes[0].toString().split(" ")[2].length()-1));
								}
								catch (Exception e) {
									// everything has failed, let aClass be the default (theClass) and hope for the best
								}
							}
					}
					else {
						// punt? wtf else could it be?
						try {
							aClass = Class.forName(aType.toString());
						}
						catch (ClassNotFoundException e) {
							// oh well, we did the best we can
						}
					}
				}
				else if (aTypes[0] instanceof Class) {
					aClass = (Class) aTypes[0];
				}
			}
			else {
				// could not figure out the type from the generics assertions on the Collection, they are either
				// not present, or my algorithm is not bullet proof.  So lets try checking on the annotations
				// for a type hint.

				Class aTarget = BeanReflectUtil.getTargetEntity(theAccessor);
				if (aTarget != null) {
					aClass = aTarget;
				}
			}
		}

		if (!BeanReflectUtil.hasAnnotation(aClass, RdfsClass.class) || aClass.isInterface()) {
			// k, so either the parameter of the collection or the declared type of the field does
			// not map to an instance/bean type.  this is most likely an error, but lets try and find
			// the rdf:type of the field, and see if we can map that to a class in the path and we'll
			// create an instance of that.  that will work, and pushes the likely failure back off to
			// the assignment of the created instance

            Iterable<Resource> aTypes = DataSourceUtil.getTypes(theSource, theId);

            // k, so now we know the type, if we can match the type to a class then we're in business
            for (Resource aType : aTypes) {
                if (aType instanceof IRI) {
                    for (Class aTypeClass : TYPE_TO_CLASS.get( (IRI) aType)) {
                        if ((BeanReflectUtil.hasAnnotation(aTypeClass, RdfsClass.class)) &&
                            (aClass.isAssignableFrom(aTypeClass))) {
                            // lets try this one
                            aClass = aTypeClass;
                            break;
                        }
                    }
                }
            }
		}

        if ( aClass.isInterface() ) {
            if ( BeanReflectUtil.hasAnnotation( aClass, RdfsClass.class) ) {
                IRI aType = FACTORY.createIRI( ((RdfsClass) aClass.getAnnotation( RdfsClass.class )).value() );
                for (Class aTypeClass : TYPE_TO_CLASS.get(aType)) {
                    if ((BeanReflectUtil.hasAnnotation(aTypeClass, RdfsClass.class)) &&
                        (aClass.isAssignableFrom(aTypeClass))) {
                        // lets try this one
                        aClass = aTypeClass;
                        return aClass;
                    }
                }
            }
        }

		return aClass;
	}

	public static class ValueToObject implements Function<Value, Object> {
		static final List<IRI> integerTypes = Arrays.asList(XMLSchema.INT, XMLSchema.INTEGER, XMLSchema.POSITIVE_INTEGER,
													  XMLSchema.NEGATIVE_INTEGER, XMLSchema.NON_NEGATIVE_INTEGER,
													  XMLSchema.NON_POSITIVE_INTEGER, XMLSchema.UNSIGNED_INT);
		static final List<IRI> longTypes = Arrays.asList(XMLSchema.LONG, XMLSchema.UNSIGNED_LONG);
		static final List<IRI> floatTypes = Arrays.asList(XMLSchema.FLOAT, XMLSchema.DECIMAL);
		static final List<IRI> shortTypes = Arrays.asList(XMLSchema.SHORT, XMLSchema.UNSIGNED_SHORT);
		static final List<IRI> byteTypes = Arrays.asList(XMLSchema.BYTE, XMLSchema.UNSIGNED_BYTE);

		private IRI mProperty;
		private Object mAccessor;
		private DataSource mSource;
		private Resource mResource;

		public ValueToObject(final DataSource theSource, Resource theResource, final Object theAccessor, final IRI theProp) {
			mResource = theResource;
			mSource = theSource;
			mAccessor = theAccessor;
			mProperty = theProp;
		}

		public Object apply(final Value theValue) {
			if (mAccessor == null) {
				throw new RuntimeException("Null accessor is not permitted");
			}

			if (theValue instanceof Literal) {
				Literal aLit = (Literal) theValue;
				IRI aDatatype = aLit.getDatatype() != null ? aLit.getDatatype() : null;
				if (aDatatype == null || XMLSchema.STRING.equals(aDatatype) || RDFS.LITERAL.equals(aDatatype)) {
					return aLit.getLabel();
				}
				else if (XMLSchema.BOOLEAN.equals(aDatatype)) {
					return Boolean.valueOf(aLit.getLabel());
				}
				else if (integerTypes.contains(aDatatype)) {
					return Integer.parseInt(aLit.getLabel());
				}
				else if (longTypes.contains(aDatatype)) {
					return Long.parseLong(aLit.getLabel());
				}
				else if (XMLSchema.DOUBLE.equals(aDatatype)) {
					return Double.valueOf(aLit.getLabel());
				}
				else if (floatTypes.contains(aDatatype)) {
					return Float.valueOf(aLit.getLabel());
				}
				else if (shortTypes.contains(aDatatype)) {
					return Short.valueOf(aLit.getLabel());
				}
				else if (byteTypes.contains(aDatatype)) {
					return Byte.valueOf(aLit.getLabel());
				}
				else if (XMLSchema.ANYURI.equals(aDatatype)) {
					try {
						return new java.net.URI(aLit.getLabel());
					}
					catch (URISyntaxException e) {
						LOGGER.warn("URI syntax exception converting literal value which is not a valid URI {} ", aLit.getLabel());
						return null;
					}
				}
				else if (XMLSchema.DATE.equals(aDatatype) || XMLSchema.DATETIME.equals(aDatatype)) {
					return Dates.asDate(aLit.getLabel());
				}
				else if (XMLSchema.TIME.equals(aDatatype)) {
					return new Date(Long.parseLong(aLit.getLabel()));
				}
				else {
					// no idea what this value is from its data type.  if the field takes a string
					// we'll just assign the plain string, otherwise its an error
					if (BeanReflectUtil.classFrom(mAccessor).isAssignableFrom(String.class)) {
						return aLit.getLabel();
					}
					else {
						throw new RuntimeException("Unsupported or unknown literal datatype");
					}
				}
			}
			else if (theValue instanceof BNode) {
				// TODO: this is not bulletproof, clean this up

				BNode aBNode = (BNode) theValue;

				// we need to figure out what type of bean this instance maps to.
				Class<?> aClass = BeanReflectUtil.classFrom(mAccessor);

				aClass = refineClass(mAccessor, aClass, mSource, aBNode);

				if (Collection.class.isAssignableFrom(BeanReflectUtil.classFrom(mAccessor))) {
					AccessibleObject aAccess = (AccessibleObject) mAccessor;
					RdfProperty aPropAnnotation = aAccess.getAnnotation(RdfProperty.class);

					// the field takes a collection, lets create a new instance of said collection, and hopefully the
					// bnode is a list.  this approach will only work if the property is a singleton value, eg
					// :inst someProperty _:a where _:a is the head of a list.  if you have another value _:b for
					// some property on :inst, we don't have any way of figuring out which one you're talking about
					// since bnode id references are not guaranteed to be stable in SPARQL, ie just because its id "a"
					// in the result set, does not mean i can do another query for _:a and get the expected results.
					// and you can't do a describe for the same reason.

					try {
						String aQuery = getBNodeConstructQuery(mSource, mResource, mProperty);
						
						Model aGraph = mSource.graphQuery(aQuery);

						Optional<Resource> aPossibleListHead = Models2.getResource(aGraph, mResource, mProperty);
						
						if (aPossibleListHead.isPresent() && Models2.isList(aGraph, aPossibleListHead.get())) {
							List<Value> aList;

							// getting the list is only safe the the query dialect supports stable bnode ids in the query language.
							if (aPropAnnotation != null && aPropAnnotation.isList() && mSource.getQueryFactory().getDialect().supportsStableBnodeIds()) {
								try {
									aList = asList(mSource, aPossibleListHead.get());
								}
								catch (DataSourceException e) {
									throw new RuntimeException(e);
								}
							}
							else {
								aList = new ArrayList<>(aGraph.filter(mResource, mProperty, null).objects());
							}

							//return new ToObjectFunction(mSource, null, (AccessibleObject) mAccessor, null).apply(aList);
							Collection<Object> aValues = BeanReflectUtil.instantiateCollectionFromField(BeanReflectUtil.classFrom(aAccess));

							for (Value aValue : aList) {
								Object aListValue = null;

								try {
									aListValue = getProxyOrDbObject(mAccessor, aClass, aValue, mSource);
								}
								catch (Exception e) {
									// we'll throw an error in a second...
								}

								if (aListValue == null) {
									throw new RuntimeException("Error converting a list value: " + aValue + " -> " + aClass);
								}

								aValues.add(aListValue);

							}
							
							return aValues;
						}
					}
					catch (QueryException e) {
						throw new RuntimeException(e);
					}
				}

				try {
					return getProxyOrDbObject(mAccessor, aClass, aBNode, mSource);
				}
				catch (Exception e) {
					if (EmpireOptions.STRICT_MODE) {
						throw new RuntimeException(e);
					}
					else {
						return null;
					}
				}
			}
			else if (theValue instanceof IRI) {
				IRI aURI = (IRI) theValue;
				try {
					// we need to figure out what type of bean this instance maps to.
					Class<?> aClass = BeanReflectUtil.classFrom(mAccessor);

					aClass = refineClass(mAccessor, aClass, mSource, aURI);

					if (aClass.isAssignableFrom(java.net.URI.class)) {
						return java.net.URI.create(aURI.toString());
					}
					else {
						return getProxyOrDbObject(mAccessor, aClass, java.net.URI.create(aURI.toString()), mSource);
					}
				}
				catch (Exception e) {
					if (EmpireOptions.STRICT_MODE) {
						throw new RuntimeException(e);
					}
					else {
						LOGGER.warn("Problem applying value {}, {} ", e.toString(), e.getCause());
						return null;
					}
				}
			}
			else {
				if (EmpireOptions.STRICT_MODE) {
					throw new RuntimeException("Unexpected Value type");
				}
				else {
					LOGGER.warn("Problem applying value : Unexpected Value type");
					return null;
				}
			}
		}
	}

	private static List<Value> asList(DataSource theSource, Resource theRes) throws DataSourceException {
        List<Value> aList = Lists.newArrayList();

        Resource aListRes = theRes;

        while (aListRes != null) {

            Resource aFirst = (Resource) DataSourceUtil.getValue(theSource, aListRes, RDF.FIRST);
            Resource aRest = (Resource) DataSourceUtil.getValue(theSource, aListRes, RDF.REST);

            if (aFirst != null) {
               aList.add(aFirst);
            }

            if (aRest == null || aRest.equals(RDF.NIL)) {
               aListRes = null;
            }
            else {
                aListRes = aRest;
            }
        }

        return aList;
	}

	private static final MethodFilter METHOD_FILTER = theMethod -> !theMethod.getName().equals("finalize");

	@SuppressWarnings("unchecked")
	private static <T> T getProxyOrDbObject(Object theAccessor, Class<T> theClass, Object theKey, DataSource theSource) throws Exception {
		if (BeanReflectUtil.isFetchTypeLazy(theAccessor)) {
			Proxy<T> aProxy = new Proxy<T>(theClass, asPrimaryKey(theKey), theSource);

			ProxyFactory aFactory = new ProxyFactory();
			if (!theClass.isInterface()) {
				aFactory.setSuperclass(theClass);
				aFactory.setInterfaces(ObjectArrays.concat(theClass.getInterfaces(), EmpireGenerated.class));
			} else {
				aFactory.setInterfaces(ObjectArrays.concat(theClass, ObjectArrays.concat(theClass.getInterfaces(), EmpireGenerated.class)));
			}

			aFactory.setFilter(METHOD_FILTER);
			final ProxyHandler<T> aHandler = new ProxyHandler<T>(aProxy);

			Object aObj = aFactory.createClass(METHOD_FILTER).newInstance();

			((ProxyObject) aObj).setHandler(aHandler);

			return (T) aObj;
		}
		else {
			return fromRdf(theClass, asPrimaryKey(theKey), theSource);
		}
	}

	/**
	 * Javassist {@link MethodHandler} implementation for method proxying.
	 * @param <T> the proxy class type
	 */
	public static class ProxyHandler<T> implements MethodHandler {
		/**
		 * The proxy object which wraps the instance being proxied.
		 */
		private Proxy<T> mProxy;

		/**
		 * Create a new ProxyHandler
		 * @param theProxy the proxy object
		 */
		private ProxyHandler(final Proxy<T> theProxy) {
			mProxy = theProxy;
		}

		public Proxy<T> getProxy() {
			return mProxy;
		}
		
		/**
		 * Delegates the methods to the Proxy
		 * @inheritDoc
		 */
		public Object invoke(final Object theThis, final Method theMethod, final Method theProxyMethod, final Object[] theArgs) throws Throwable {

			return theMethod.invoke(mProxy.value(), theArgs);
		}
	}
	
	private static String getBNodeConstructQuery(DataSource theSource, Resource theRes, IRI theProperty) {
		Dialect aDialect = theSource.getQueryFactory().getDialect();

		String aSerqlQuery = "construct * from {" + aDialect.asQueryString(theRes) + "} <" + theProperty.toString() + "> {o}, {o} po {oo}";

		String aSparqlQuery = "CONSTRUCT  { " + aDialect.asQueryString(theRes) + " <"+theProperty.toString()+"> ?o . ?o ?po ?oo  } \n" +
							  "WHERE\n" +
							  "{ " + aDialect.asQueryString(theRes) + " <" + theProperty.toString() + "> ?o.\n" +
							  "?o ?po ?oo. }";

		if (theSource.getQueryFactory().getDialect() instanceof SerqlDialect) {
			return aSerqlQuery;
		}
		else {
			// TODO: we're just assuming/hoping at this point that they support sparql.  which
			// will most likely be the case, but possibly not always.
			return aSparqlQuery;
		}
	}

	public static class AsValueFunction implements Function<Object, Value> {
		private AccessibleObject mField;
		private RdfProperty annotation;

		public AsValueFunction() {
		}

		public AsValueFunction(final AccessibleObject theField) {
			mField = theField;
			annotation = mField == null ? null : mField.getAnnotation(RdfProperty.class);
		}

		public Value apply(final Object theIn) {
			if (theIn == null) {
				return null;
			}
            else if (!EmpireOptions.STRONG_TYPING && BeanReflectUtil.isPrimitive(theIn)) {
                return FACTORY.createLiteral(theIn.toString());
            }
			else if (Boolean.class.isInstance(theIn)) {
				return FACTORY.createLiteral(Boolean.class.cast(theIn));
			}
			else if (Integer.class.isInstance(theIn)) {
				return FACTORY.createLiteral(Integer.class.cast(theIn));
			}
			else if (Long.class.isInstance(theIn)) {
				return FACTORY.createLiteral(Long.class.cast(theIn));
			}
			else if (Short.class.isInstance(theIn)) {
				return FACTORY.createLiteral(Short.class.cast(theIn));
			}
			else if (Double.class.isInstance(theIn)) {
				return FACTORY.createLiteral(Double.class.cast(theIn));
			}
			else if (Float.class.isInstance(theIn)) {
				return FACTORY.createLiteral(Float.class.cast(theIn));
			}
			else if (Date.class.isInstance(theIn)) {
				return FACTORY.createLiteral(Dates.datetime(Date.class.cast(theIn)), XMLSchema.DATETIME);
			}
			else if (String.class.isInstance(theIn)) {
				if (annotation != null && !annotation.language().equals("")) {
					return FACTORY.createLiteral(String.class.cast(theIn), annotation.language());
				}
				else {
					return FACTORY.createLiteral(String.class.cast(theIn), XMLSchema.STRING);
				}
			}
			else if (Character.class.isInstance(theIn)) {
				return FACTORY.createLiteral(Character.class.cast(theIn));
			}
			else if (java.net.URI.class.isInstance(theIn)) {
				if (annotation != null && annotation.isXsdUri()) {
					return FACTORY.createLiteral(theIn.toString(), XMLSchema.ANYURI);
				}
				else {
                	return FACTORY.createIRI(theIn.toString());
				}
			}
			else if (Value.class.isAssignableFrom(theIn.getClass())) {
				return Value.class.cast(theIn);
			}
			else if (BeanReflectUtil.hasAnnotation(theIn.getClass(), RdfsClass.class)) {
				try {
					return id(theIn);
				}
				catch (InvalidRdfException e) {
					throw new RuntimeException(e);
				}
			}
			else if (theIn instanceof ProxyHandler) {
				return this.apply( ((ProxyHandler)theIn).mProxy.value());
			}
			else {
				try {
					Field aProxy =  theIn.getClass().getDeclaredField("handler");
					return this.apply(((ProxyHandler)BeanReflectUtil.safeGet(aProxy, theIn)).mProxy.value());
				}
				catch (Exception e) {
					throw new RuntimeException("Unknown type conversion: " + theIn.getClass() + " " + theIn + " " + mField);
				}
			}
		}
	}

	private static class ContainsResourceValues implements Predicate<Value> {
		public boolean test(final Value theValue) {
			return theValue instanceof Resource;
		}
	}

	private static class LanguageFilter implements Predicate<Value> {
		private String mLangCode;

		private LanguageFilter(final String theLangCode) {
			mLangCode = theLangCode;
		}

		public void setLangCode(final String theLangCode) {
			mLangCode = theLangCode;
		}

		public boolean test(final Value theValue) {
			return theValue instanceof Literal && mLangCode.equals(((Literal)theValue).getLanguage());
		}
	}
}
