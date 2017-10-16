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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonar.java.cfg.CFG;
import org.sonar.java.se.constraint.BooleanConstraint;
import org.sonar.java.se.constraint.ConstraintsByDomain;
import org.sonar.java.se.constraint.ObjectConstraint;
import org.sonar.java.se.dto.HappyPathMethodYieldDto;
import org.sonar.java.se.dto.MethodYieldDto;
import org.sonar.java.se.dto.NodeDetailsDto;
import org.sonar.java.se.dto.NodeDetailsWithYieldDto;
import org.sonar.java.se.dto.SvWithConstraintsDto;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.java.se.xproc.BehaviorCache;
import org.sonar.java.se.xproc.HappyPathYield;
import org.sonar.java.se.xproc.MethodBehavior;
import org.sonar.java.viewer.DotGraph;
import org.sonar.java.viewer.Viewer;
import org.sonar.plugins.java.api.semantic.Symbol;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EGDotNodeTest {
  private static final Symbol A_SYMBOL = mockSymbol("a");
  private static final SymbolicValue SV_42 = mockSymbolicValue(42);
  private static final SymbolicValue SV_21 = mockSymbolicValue(21);

  private BehaviorCache mockBehaviorCache;
  private ExplodedGraph eg;

  private static Symbol mockSymbol(String symbolName) {
    Symbol mock = Mockito.mock(Symbol.class);
    Mockito.when(mock.toString()).thenReturn(symbolName);
    Mockito.when(mock.name()).thenReturn(symbolName);
    return mock;
  }

  private static SymbolicValue mockSymbolicValue(int id) {
    SymbolicValue sv = Mockito.mock(SymbolicValue.class);
    Mockito.when(sv.toString()).thenReturn("SV_" + id);
    return sv;
  }

  @Before
  public void setUp() {
    mockBehaviorCache = Mockito.mock(BehaviorCache.class);
    eg = new ExplodedGraph();
  }

  @Test
  public void lost_node_has_specific_highlighting() {
    String source = "class A {"
      + "  void foo() {"
      + "    doSomething();"
      + "  }"
      + "}";

    Viewer.Base base = new Viewer.Base(source);

    // no parent, fake id of first block being 42, block id being 0
    ExplodedGraph.Node node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 0);
    EGDotNode egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 42);

    assertThat(egDotNode.highlighting()).isEqualTo(DotGraph.Highlighting.LOST_NODE);
  }

  @Test
  public void node_contains_program_point() {
    String source = "class A {"
      + "  void foo() {"
      + "    doSomething();"
      + "  }"
      + "}";

    Viewer.Base base = new Viewer.Base(source);
    ExplodedGraph.Node node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 0);

    EGDotNode egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);
    NodeDetailsDto details = egDotNode.details();

    assertThat(details.ppKey).isEqualTo("B1.0");
  }

  @Test
  public void node_contains_values() {
    String source = "class A {"
      + "  void foo() {"
      + "    doSomething();"
      + "  }"
      + "}";

    Viewer.Base base = new Viewer.Base(source);
    ExplodedGraph.Node node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 0);

    EGDotNode egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);
    NodeDetailsDto details = egDotNode.details();

    assertThat(details.psValues).isEmpty();

    ProgramState newPs = node.programState.put(A_SYMBOL, SV_42);
    node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 0, newPs);
    egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);
    details = egDotNode.details();

    assertThat(details.psValues).hasSize(1);
    assertThat(details.psValues.get(0).sv).isEqualTo("SV_42");
    assertThat(details.psValues.get(0).symbol).isEqualTo("a");
  }

  @Test
  public void node_contains_constraints() {
    String source = "class A {"
      + "  void foo() {"
      + "    doSomething();"
      + "  }"
      + "}";

    Viewer.Base base = new Viewer.Base(source);
    ExplodedGraph.Node node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 0);

    EGDotNode egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);
    NodeDetailsDto details = egDotNode.details();

    // contains starting SVs TRUE, FALSE, NULL
    assertThat(details.psConstraints).hasSize(3);
    assertThat(details.psConstraints).contains(new SvWithConstraintsDto("SV_NULL", "NULL"));
    assertThat(details.psConstraints).contains(new SvWithConstraintsDto("SV_FALSE", Arrays.asList("FALSE", "NOT_NULL")));
    assertThat(details.psConstraints).contains(new SvWithConstraintsDto("SV_TRUE", Arrays.asList("NOT_NULL", "TRUE")));

    ProgramState newPs = node.programState.addConstraint(SV_42, ObjectConstraint.NOT_NULL);
    node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 0, newPs);
    egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);
    details = egDotNode.details();

    assertThat(details.psConstraints).hasSize(4);
    assertThat(details.psConstraints).contains(new SvWithConstraintsDto("SV_42", "NOT_NULL"));
  }

  @Test
  public void node_contains_elements_of_stack() {
    String source = "class A {"
      + "  void foo() {"
      + "    doSomething();"
      + "  }"
      + "}";

    Viewer.Base base = new Viewer.Base(source);
    ExplodedGraph.Node node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 0);

    EGDotNode egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);
    NodeDetailsDto details = egDotNode.details();

    assertThat(details.psStack).isEmpty();

    ProgramState newPs = node.programState.stackValue(SV_42);
    node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 0, newPs);
    egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);
    details = egDotNode.details();

    assertThat(details.psStack).hasSize(1);
    assertThat(details.psStack.get(0).sv).isEqualTo("SV_42");
    assertThat(details.psStack.get(0).symbol).isNull();

    newPs = node.programState.stackValue(SV_21, A_SYMBOL);
    node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 0, newPs);
    egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);
    details = egDotNode.details();

    assertThat(details.psStack).hasSize(2);
    assertThat(details.psStack.get(0).sv).isEqualTo("SV_21");
    assertThat(details.psStack.get(0).symbol).isEqualTo("a");
    assertThat(details.psStack.get(1).sv).isEqualTo("SV_42");
    assertThat(details.psStack.get(1).symbol).isNull();
  }

  @Test
  public void node_without_method_invocation_has_nothing_regarding_methods() {
    String source = "class A {"
      + "  void foo() {"
      + "    int i = 1;"
      + "  }"
      + "}";

    Viewer.Base base = new Viewer.Base(source);

    ExplodedGraph.Node node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 0);
    EGDotNode egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);

    NodeDetailsDto details = egDotNode.details();
    assertThat(details).isNotInstanceOf(NodeDetailsWithYieldDto.class);
  }

  @Test
  public void unknown_method_does_not_populate_methodYields_and_methodName() {
    String source = "class A {"
      + "  void foo() {"
      + "    doSomething();"
      + "  }"
      + "}";

    Viewer.Base base = new Viewer.Base(source);

    // node of method invocation
    ExplodedGraph.Node node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 1);
    EGDotNode egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);

    NodeDetailsDto details = egDotNode.details();
    assertThat(details).isNotInstanceOf(NodeDetailsWithYieldDto.class);
  }

  @Test
  public void method_with_behavior_populate_methodYields_and_methodName() {
    String source = "abstract class A {"
      + "  void foo() {"
      + "    doSomething();"
      + "  }"
      + "  abstract Boolean doSomething();"
      + "}";

    Viewer.Base base = new Viewer.Base(source);

    Mockito.when(mockBehaviorCache.get(Mockito.any(Symbol.MethodSymbol.class))).thenAnswer(new Answer<MethodBehavior>() {
      @Override
      public MethodBehavior answer(InvocationOnMock invocation) throws Throwable {
        Symbol.MethodSymbol methodSymbol = invocation.getArgument(0);
        MethodBehavior mb = new MethodBehavior(methodSymbol);
        HappyPathYield hpy = new HappyPathYield(mb);
        hpy.setResult(-1, null);
        mb.addYield(hpy);
        return mb;
      }
    });

    ExplodedGraph.Node node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 1);
    EGDotNode egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);

    NodeDetailsDto details = egDotNode.details();
    assertThat(details).isExactlyInstanceOf(NodeDetailsWithYieldDto.class);

    NodeDetailsWithYieldDto detailsWithYield = (NodeDetailsWithYieldDto) details;

    assertThat(detailsWithYield.methodName).isNotNull();
    assertThat(detailsWithYield.methodName).isEqualTo("doSomething");

    List<MethodYieldDto> yields = detailsWithYield.methodYields;
    assertThat(yields).isNotNull();
    assertThat(yields).hasSize(1);

    MethodYieldDto yield0 = yields.get(0);
    assertThat(yield0.params).isEmpty();
    assertThat(yield0).isInstanceOf(HappyPathMethodYieldDto.class);

    HappyPathMethodYieldDto hpy0 = (HappyPathMethodYieldDto) yield0;
    assertThat(hpy0.resultIndex).isEqualTo(-1);
    assertThat(hpy0.result).containsExactly("no constraint");
  }

  @Test
  public void method_with_behavior_populate_methodYields_and_methodName_with_constraints_and_index() {
    String source = "abstract class A {"
      + "  void foo() {"
      + "    doSomething();"
      + "  }"
      + "  abstract Boolean doSomething();"
      + "}";

    Viewer.Base base = new Viewer.Base(source);

    Mockito.when(mockBehaviorCache.get(Mockito.any(Symbol.MethodSymbol.class))).thenAnswer(new Answer<MethodBehavior>() {
      @Override
      public MethodBehavior answer(InvocationOnMock invocation) throws Throwable {
        Symbol.MethodSymbol methodSymbol = invocation.getArgument(0);
        MethodBehavior mb = new MethodBehavior(methodSymbol);
        HappyPathYield hpy = new HappyPathYield(mb);
        hpy.setResult(2, ConstraintsByDomain.empty().put(ObjectConstraint.NOT_NULL).put(BooleanConstraint.FALSE));
        mb.addYield(hpy);
        return mb;
      }
    });

    ExplodedGraph.Node node = newNode(base.cfgFirstMethodOrConstructor.blocks().get(0), 1);
    EGDotNode egDotNode = new EGDotNode(0, node, mockBehaviorCache, false, 1);

    NodeDetailsDto details = egDotNode.details();
    assertThat(details).isExactlyInstanceOf(NodeDetailsWithYieldDto.class);

    NodeDetailsWithYieldDto detailsWithYield = (NodeDetailsWithYieldDto) details;

    assertThat(detailsWithYield.methodName).isNotNull();
    assertThat(detailsWithYield.methodName).isEqualTo("doSomething");

    List<MethodYieldDto> yields = detailsWithYield.methodYields;
    assertThat(yields).isNotNull();
    assertThat(yields).hasSize(1);

    MethodYieldDto yield0 = yields.get(0);
    assertThat(yield0.params).isEmpty();
    assertThat(yield0).isInstanceOf(HappyPathMethodYieldDto.class);

    HappyPathMethodYieldDto hpy0 = (HappyPathMethodYieldDto) yield0;
    assertThat(hpy0.resultIndex).isEqualTo(2);
    // order is alphabetical for constraint
    assertThat(hpy0.result).containsExactly("FALSE", "NOT_NULL");
  }

  private ExplodedGraph.Node newNode(CFG.Block block, int i) {
    return newNode(block, i, ProgramState.EMPTY_STATE);
  }

  private ExplodedGraph.Node newNode(CFG.Block block, int i, ProgramState ps) {
    ProgramPoint pp = new ProgramPoint(block);
    for (int j = 0; j < i; j++) {
      pp = pp.next();
    }
    return eg.node(pp, ps);
  }

}
