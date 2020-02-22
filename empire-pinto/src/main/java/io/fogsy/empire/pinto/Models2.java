//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package io.fogsy.empire.pinto;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.eclipse.rdf4j.common.iteration.Iteration;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import io.fogsy.empire.cp.openrdf.utils.model.ModelIO;
import io.fogsy.empire.cp.openrdf.utils.model.Statements;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class Models2 {
  private Models2() {
    throw new AssertionError();
  }

  public static Collector<Statement, Model, Model> toModel() {
    return new Collector<Statement, Model, Model>() {
      public Supplier<Model> supplier() {
        return Models2::newModel;
      }

      public BiConsumer<Model, Statement> accumulator() {
        return Set::add;
      }

      public BinaryOperator<Model> combiner() {
        return (theGraph, theOtherGraph) -> {
          theGraph.addAll(theOtherGraph);
          return theGraph;
        };
      }

      public Function<Model, Model> finisher() {
        return Function.identity();
      }

      public Set<Characteristics> characteristics() {
        return Sets.newHashSet(new Characteristics[]{Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED});
      }
    };
  }

  public static Model of(Path thePath) throws IOException {
    return ModelIO.read(thePath);
  }

  public static Model newModel() {
    return new LinkedHashModel();
  }

  public static Model newModel(Iterable<Statement> theStmts) {
    Model aModel = newModel();
    Iterables.addAll(aModel, theStmts);
    return aModel;
  }

  public static Model newModel(Iterator<Statement> theStmts) {
    Model aModel = newModel();
    Iterators.addAll(aModel, theStmts);
    return aModel;
  }

  public static Model newModel(Statement... theStmts) {
    Model aModel = newModel();
    Collections.addAll(aModel, theStmts);
    return aModel;
  }

  public static <E extends Exception, T extends Iteration<Statement, E>> Model newModel(T theStmts) throws E {
    Model aModel = newModel();
    Iterations.stream(theStmts).forEach(aModel::add);
    return aModel;
  }

  public static Model withContext(Iterable<Statement> theGraph, Resource theResource) {
    Model aModel = newModel();
    Iterator var3 = theGraph.iterator();

    while(var3.hasNext()) {
      Statement aStmt = (Statement)var3.next();
      aModel.add(SimpleValueFactory.getInstance().createStatement(aStmt.getSubject(), aStmt.getPredicate(), aStmt.getObject(), theResource));
    }

    return aModel;
  }

  public static Model union(Model... theGraphs) {
    Model aModel = newModel();
    Model[] var2 = theGraphs;
    int var3 = theGraphs.length;

    for(int var4 = 0; var4 < var3; ++var4) {
      Model aGraph = var2[var4];
      aModel.addAll(aGraph);
    }

    return aModel;
  }

  public static Optional<Value> getObject(Model theGraph, Resource theSubj, IRI thePred) {
    Iterator<Value> aCollection = theGraph.filter(theSubj, thePred, (Value)null, new Resource[0]).objects().iterator();
    return aCollection.hasNext() ? Optional.of(aCollection.next()) : Optional.empty();
  }

  public static Optional<Literal> getLiteral(Model theGraph, Resource theSubj, IRI thePred) {
    Optional<Value> aVal = getObject(theGraph, theSubj, thePred);
    return aVal.isPresent() && aVal.get() instanceof Literal ? Optional.of((Literal)aVal.get()) : Optional.empty();
  }

  public static Optional<Resource> getResource(Model theGraph, Resource theSubj, IRI thePred) {
    Optional<Value> aVal = getObject(theGraph, theSubj, thePred);
    return aVal.isPresent() && aVal.get() instanceof Resource ? Optional.of((Resource)aVal.get()) : Optional.empty();
  }

  public static Optional<Boolean> getBooleanValue(Model theGraph, Resource theSubj, IRI thePred) {
    Optional<Literal> aLitOpt = getLiteral(theGraph, theSubj, thePred);
    if (!aLitOpt.isPresent()) {
      return Optional.empty();
    } else {
      Literal aLiteral = (Literal)aLitOpt.get();
      return (aLiteral.getDatatype() == null || !aLiteral.getDatatype().equals(XMLSchema.BOOLEAN)) && !aLiteral.getLabel().equalsIgnoreCase("true") && !aLiteral.getLabel().equalsIgnoreCase("false") ? Optional.empty() : Optional.of(Boolean.valueOf(aLiteral.getLabel()));
    }
  }

  public static boolean isList(Model theGraph, Resource theRes) {
    return theRes != null && (theRes.equals(RDF.NIL) || theGraph.stream().filter(Statements.matches(theRes, RDF.FIRST, (Value)null, new Resource[0])).findFirst().isPresent());
  }

  public static List<Value> asList(Model theGraph, Resource theRes) {
    List<Value> aList = Lists.newArrayList();
    Resource aListRes = theRes;

    while(aListRes != null) {
      Optional<Value> aFirst = getObject(theGraph, aListRes, RDF.FIRST);
      Optional<Resource> aRest = getResource(theGraph, aListRes, RDF.REST);
      if (aFirst.isPresent()) {
        aList.add(aFirst.get());
      }

      if (((Resource)aRest.orElse(RDF.NIL)).equals(RDF.NIL)) {
        aListRes = null;
      } else {
        aListRes = (Resource)aRest.get();
      }
    }

    return aList;
  }

  public static Model toList(List<Value> theResources) {
    Model aResult = newModel();
    toList(theResources, aResult);
    return aResult;
  }

  public static Resource toList(List<Value> theResources, Model theGraph) {
    Resource aCurr = SimpleValueFactory.getInstance().createBNode();
    int i = 0;

    BNode aNext;
    for(Iterator var5 = theResources.iterator(); var5.hasNext(); aCurr = aNext) {
      Value aRes = (Value)var5.next();
      aNext = SimpleValueFactory.getInstance().createBNode();
      theGraph.add(aCurr, RDF.FIRST, aRes, new Resource[0]);
      ++i;
      theGraph.add(aCurr, RDF.REST, (Value)(i < theResources.size() ? aNext : RDF.NIL), new Resource[0]);
    }

    return aCurr;
  }

  public static Iterable<Resource> getTypes(Model theGraph, Resource theRes) {
    return (Iterable)theGraph.stream().filter(Statements.matches(theRes, RDF.TYPE, (Value)null, new Resource[0])).map(Statement::getObject).map((theObject) -> {
      return (Resource)theObject;
    }).collect(Collectors.toList());
  }

  public static boolean isInstanceOf(Model theGraph, Resource theSubject, Resource theType) {
    return theGraph.contains(SimpleValueFactory.getInstance().createStatement(theSubject, RDF.TYPE, theType));
  }
}
