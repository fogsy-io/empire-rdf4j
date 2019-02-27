/*
 * Copyright 2018 FZI Forschungszentrum Informatik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.streampipes.empire.pinto;


import org.streampipes.empire.annotations.RdfId;
import org.streampipes.empire.annotations.RdfProperty;

import java.util.List;

/**
 * named SEPA elements, can be accessed via the URI provided in @RdfId
 */
public abstract class NamedStreamPipesEntity extends AbstractStreamPipesEntity {

  private static final long serialVersionUID = -98951691820519795L;


  @RdfProperty("http://test.de/hasUri")
  @RdfId
  private String uri;

  protected String elementId;

  protected String DOM;
  protected List<String> connectedTo;


  public NamedStreamPipesEntity() {
    super();
  }

  public NamedStreamPipesEntity(String uri) {
    super();
    this.uri = uri;
  }

  public NamedStreamPipesEntity(String uri, String name, String description, String iconUrl) {
    this(uri, name, description);
  }

  public NamedStreamPipesEntity(String uri, String name, String description) {
    super();
    this.uri = uri;
    this.elementId = uri;
  }

  public NamedStreamPipesEntity(NamedStreamPipesEntity other) {
    super();
    this.uri = other.getUri();
    this.DOM = other.getDOM();
    this.connectedTo = other.getConnectedTo();
    this.elementId = other.getElementId();
  }


  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = uri;
  }

  public String getElementId() {
    return uri;
  }

  public void setElementId(String elementId) {
    this.uri = elementId;
  }

  public void setDOM(String DOM) {
    this.DOM = DOM;
  }

  public String getDOM() {
    return DOM;
  }

  public List<String> getConnectedTo() {
    return connectedTo;
  }

  public void setConnectedTo(List<String> connectedTo) {
    this.connectedTo = connectedTo;
  }


}
