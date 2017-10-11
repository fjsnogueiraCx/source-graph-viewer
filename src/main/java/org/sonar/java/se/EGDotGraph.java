/*
 * SonarQube SourgeGraph Viewer
 * Copyright (C) 2017-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.se;

import com.google.common.collect.Lists;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.sonar.java.bytecode.loader.SquidClassLoader;
import org.sonar.java.resolve.JavaSymbol;
import org.sonar.java.resolve.SemanticModel;
import org.sonar.java.se.xproc.BehaviorCache;
import org.sonar.java.se.xproc.MethodBehavior;
import org.sonar.java.viewer.DotGraph;
import org.sonar.java.viewer.Viewer;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.CompilationUnitTree;
import org.sonar.plugins.java.api.tree.MethodTree;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EGDotGraph extends DotGraph {

  private static final boolean SHOW_MULTIPLE_PARENTS = true;

  private ExplodedGraph explodedGraph;
  private BehaviorCache behaviorCache;
  private final CompilationUnitTree cut;
  private final MethodTree methodToAnalyze;
  private final SemanticModel semanticModel;
  private final SquidClassLoader classLoader;
  private final int cfgFirstBlockId;

  public EGDotGraph(Viewer.Base base) {
    this(base.cut, base.firstMethodOrConstructor, base.semanticModel, base.classLoader, base.cfgFirstMethodOrConstructor.blocks().get(0).id());
  }

  private EGDotGraph(CompilationUnitTree cut, MethodTree method, SemanticModel semanticModel, SquidClassLoader classLoader, int cfgFirstBlockId) {
    this.cut = cut;
    this.methodToAnalyze = method;
    this.semanticModel = semanticModel;
    this.cfgFirstBlockId = cfgFirstBlockId;
    this.classLoader = classLoader;
    computeEG();
  }

  private void computeEG() {
    JavaFileScannerContext mockContext = mock(JavaFileScannerContext.class);
    when(mockContext.getTree()).thenReturn(cut);
    when(mockContext.getSemanticModel()).thenReturn(semanticModel);
    // explicitly enable X-File analysis
    this.behaviorCache = new BehaviorCache(classLoader, true);
    SymbolicExecutionVisitor sev = new SymbolicExecutionVisitor(Lists.newArrayList(), behaviorCache) {
      @Override
      public void execute(MethodTree methodTree) {
        this.context = mockContext;
        super.execute(methodTree);
      }
    };
    sev.behaviorCache.setFileContext(sev, semanticModel);
    ExplodedGraphWalker.ExplodedGraphWalkerFactory egwFactory = new ExplodedGraphWalker.ExplodedGraphWalkerFactory(Collections.emptyList());
    ExplodedGraphWalker walker = egwFactory.createWalker(sev.behaviorCache, semanticModel);
    walker.visitMethod(methodToAnalyze, new MethodBehavior(((JavaSymbol.MethodJavaSymbol) methodToAnalyze.symbol()).completeSignature()));

    this.explodedGraph = getExplodedGraph(walker);
  }

  private static ExplodedGraph getExplodedGraph(ExplodedGraphWalker walker) {
    try {
      // ugly hack to get the exploded graph field
      Field explodedGraphField = walker.getClass().getDeclaredField("explodedGraph");
      explodedGraphField.setAccessible(true);
      return (ExplodedGraph) explodedGraphField.get(walker);
    } catch (Exception e) {
      // do nothing
    }
    return null;
  }

  @Override
  public String name() {
    return "ExplodedGraph";
  }

  @Override
  public void build() {
    List<ExplodedGraph.Node> egNodes = new ArrayList<>(explodedGraph.nodes().keySet());
    int index = 0;
    for (ExplodedGraph.Node node : egNodes) {
      Collection<ExplodedGraph.Edge> egEdges = node.edges();
      addNode(new EGDotNode(index, node, behaviorCache, semanticModel, !egEdges.isEmpty(), cfgFirstBlockId));
      Stream<ExplodedGraph.Edge> edgeStream = egEdges.stream();
      if (!SHOW_MULTIPLE_PARENTS) {
        edgeStream = edgeStream.limit(1);
      }
      int finalIndex = index;
      edgeStream.map(e -> new EGDotEdge(egNodes.indexOf(e.parent()), finalIndex, e, semanticModel)).forEach(this::addEdge);
      index++;
    }
  }
}
