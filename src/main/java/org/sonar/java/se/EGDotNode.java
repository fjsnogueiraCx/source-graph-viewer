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

import org.sonar.java.cfg.CFG;
import org.sonar.java.collections.PStack;
import org.sonar.java.se.ProgramState.SymbolicValueSymbol;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.constraint.ConstraintsByDomain;
import org.sonar.java.se.dto.ExceptionPathMethodYieldDto;
import org.sonar.java.se.dto.HappyPathMethodYieldDto;
import org.sonar.java.se.dto.MethodYieldDto;
import org.sonar.java.se.dto.NodeDetailsDto;
import org.sonar.java.se.dto.NodeDetailsWithYieldDto;
import org.sonar.java.se.dto.SvWithConstraintsDto;
import org.sonar.java.se.dto.SvWithSymbolDto;
import org.sonar.java.se.xproc.BehaviorCache;
import org.sonar.java.se.xproc.ExceptionalYield;
import org.sonar.java.se.xproc.HappyPathYield;
import org.sonar.java.se.xproc.MethodBehavior;
import org.sonar.java.se.xproc.MethodYield;
import org.sonar.java.viewer.DotGraph;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.Tree;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EGDotNode extends DotGraph.Node {

  private final ProgramState ps;
  private final ProgramPoint pp;
  @Nullable
  private final MethodBehavior methodBehavior;
  private final boolean hasParents;
  private final boolean isFirstBlock;

  private final NodeDetailsDto details;

  public EGDotNode(int id, ExplodedGraph.Node node, BehaviorCache behaviorCache, boolean hasParents, int firstBlockId) {
    super(id);
    this.ps = node.programState;
    this.pp = node.programPoint;
    this.hasParents = hasParents;
    this.isFirstBlock = isFirstBlock(node, firstBlockId);
    this.methodBehavior = getMethodBehavior(behaviorCache, pp.syntaxTree());

    this.details = buildDetails();
  }

  private static boolean isFirstBlock(ExplodedGraph.Node node, int firstBlockId) {
    return node.programPoint.toString().startsWith("B" + firstBlockId + "." + "0");
  }

  @Override
  public String label() {
    return programPoint();
  }

  @Override
  @CheckForNull
  public DotGraph.Highlighting highlighting() {
    if (!hasParents) {
      if (isFirstBlock) {
        return DotGraph.Highlighting.FIRST_NODE;
      }
      // lost node with no parents, but not first node - should never happen - worth investigation if appears in viewer
      return DotGraph.Highlighting.LOST_NODE;
    } else if (programPoint().startsWith("B0.0")) {
      return DotGraph.Highlighting.EXIT_NODE;
    }
    return null;
  }

  private NodeDetailsDto buildDetails() {
    String programPointKey = programPointKey();
    List<SvWithSymbolDto> stack = stack();
    List<SvWithConstraintsDto> constraints = constraints();
    List<SvWithSymbolDto> values = values();
    if (hasMethodBehavior()) {
      return new NodeDetailsWithYieldDto(programPointKey, stack, constraints, values, methodName(), yields());
    }
    return new NodeDetailsDto(programPointKey, stack, constraints, values);
  }

  @Override
  public NodeDetailsDto details() {
    return details;
  }

  private List<SvWithSymbolDto> values() {
    Stream.Builder<SvWithSymbolDto> builder = Stream.builder();
    ps.values.forEach((symbol, sv) -> builder.add(new SvWithSymbolDto(sv.toString(), symbol.toString())));
    return builder.build().sorted().collect(Collectors.toList());
  }

  private List<SvWithConstraintsDto> constraints() {
    Stream.Builder<SvWithConstraintsDto> builder = Stream.builder();
    ps.constraints.forEach((sv, constraint) -> builder.add(new SvWithConstraintsDto(sv.toString(), constraints(constraint))));
    return builder.build().sorted().collect(Collectors.toList());
  }

  private static List<String> constraints(@Nullable ConstraintsByDomain constraintsByDomain) {
    Stream.Builder<String> builder = Stream.builder();
    if (constraintsByDomain == null || constraintsByDomain.isEmpty()) {
      builder.add("no constraint");
    } else {
      constraintsByDomain.stream().map(Constraint::toString).forEach(builder::add);
    }
    return builder.build().sorted().collect(Collectors.toList());
  }

  private static List<List<String>> constraints(List<ConstraintsByDomain> constraints) {
    return constraints.stream().map(EGDotNode::constraints).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private List<SvWithSymbolDto> stack() {
    // Ugly hack to get the stack and not expose programState API.
    // The stack should remain private to avoid uncontrolled usage in engine
    Stream.Builder<SvWithSymbolDto> builder = Stream.builder();
    try {
      Field stackField = ps.getClass().getDeclaredField("stack");
      stackField.setAccessible(true);
      PStack<SymbolicValueSymbol> stack = (PStack<SymbolicValueSymbol>) stackField.get(ps);
      stack.forEach(svs -> {
        Symbol symbol = svs.symbol;
        builder.add(new SvWithSymbolDto(svs.sv.toString(), symbol != null ? symbol.toString() : null));
      });
    } catch (Exception e) {
      // do nothing
    }
    return builder.build().collect(Collectors.toList());
  }

  private String programPointKey() {
    return "B" + pp.block.id() + "." + pp.i;
  }

  private String programPoint() {
    String tree = "";
    if (pp.i < pp.block.elements().size()) {
      Tree syntaxNode = ((CFG.Block) pp.block).elements().get(pp.i);
      tree = "" + syntaxNode.kind() + " L#" + syntaxNode.firstToken().line();
    }
    return programPointKey() + "  " + tree;
  }

  private List<MethodYieldDto> yields() {
    return methodBehavior.yields().stream().map(EGDotNode::yield).collect(Collectors.toList());
  }

  private boolean hasMethodBehavior() {
    return methodBehavior != null;
  }

  @CheckForNull
  private static MethodBehavior getMethodBehavior(BehaviorCache bc, @Nullable Tree syntaxTree) {
    if (syntaxTree == null || !syntaxTree.is(Tree.Kind.METHOD_INVOCATION)) {
      return null;
    }
    Symbol symbol = ((MethodInvocationTree) syntaxTree).symbol();
    if (!symbol.isMethodSymbol()) {
      return null;
    }
    return bc.get((Symbol.MethodSymbol) symbol);
  }

  public static MethodYieldDto yield(MethodYield methodYield) {
    List<List<String>> params = constraints(getParametersConstraints(methodYield));
    if (methodYield instanceof HappyPathYield) {
      HappyPathYield hpy = (HappyPathYield) methodYield;
      return new HappyPathMethodYieldDto(params, constraints(hpy.resultConstraint()), hpy.resultIndex());
    } else if (methodYield instanceof ExceptionalYield) {
      Type exceptionType = ((ExceptionalYield) methodYield).exceptionType();
      String exceptionFQN = exceptionType == null ? "runtime Exception" : exceptionType.fullyQualifiedName();
      return new ExceptionPathMethodYieldDto(params, exceptionFQN);
    }
    return new MethodYieldDto(params);
  }

  @SuppressWarnings("unchecked")
  private static List<ConstraintsByDomain> getParametersConstraints(MethodYield methodYield) {
    // Ugly hack to get method yield parameters without exposing MethodYield API
    try {
      Field parametersConstraintsField = MethodYield.class.getDeclaredField("parametersConstraints");
      parametersConstraintsField.setAccessible(true);
      return (List<ConstraintsByDomain>) parametersConstraintsField.get(methodYield);
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  private String methodName() {
    return methodBehavior.methodSymbol().name();
  }

}
