/*
 * Copyright (c) 2015 Complexible Inc. <http://complexible.com>
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

package org.streampipes.empire.pinto.codecs;

import com.google.common.base.Preconditions;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.streampipes.empire.cp.openrdf.utils.model.Models2;
import org.streampipes.empire.cp.openrdf.utils.util.ResourceBuilder;
import org.streampipes.empire.pinto.RDFCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * <p>Codec for (de)serializing {@link UUID}.</p>
 *
 * @author  Michael Grove
 * @since   1.0
 * @version 2.0
 */
public enum UUIDCodec implements RDFCodec<UUID> {
	Instance;

	public static final IRI TYPE = SimpleValueFactory.getInstance().createIRI("tag:java.util.UUID");
	public static final IRI PROPERTY = SimpleValueFactory.getInstance().createIRI("tag:java.util.UUID:uuid");

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResourceBuilder writeValue(final UUID theValue) {
		return new ResourceBuilder(SimpleValueFactory.getInstance().createBNode())
				.addType(TYPE)
				.addProperty(PROPERTY, SimpleValueFactory.getInstance().createLiteral(theValue.toString()));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UUID readValue(final Model theGraph, final Value theObj) {
		Preconditions.checkArgument(theObj instanceof Resource);

		final Optional<Value> aObject = Models2.getObject(theGraph, (Resource) theObj, PROPERTY);

		if (aObject.isPresent()) {
			return UUID.fromString(aObject.get().stringValue());
		}

		return null;
	}
}