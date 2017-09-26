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

import org.sonar.java.se.dto.EdgeDetailsDto;
import org.sonar.java.se.dto.MethodYieldDto;
import org.sonar.java.se.dto.SvWithConstraintsDto;
import org.sonar.java.se.dto.SvWithSymbolDto;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.java.viewer.DotGraph;

import javax.annotation.CheckForNull;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EGDotEdge extends DotGraph.Edge {

  private final ExplodedGraph.Edge edge;
  private final EdgeDetailsDto details;

  public EGDotEdge(int from, int to, ExplodedGraph.Edge edge) {
    super(from, to);
    this.edge = edge;
    this.details = buildDetails();
  }

  @Override
  public String label() {
    Stream<String> learnedConstraints = edge.learnedConstraints().stream()
      .sorted(Comparator.comparing(lc -> lc.sv.toString()))
      .map(LearnedConstraint::toString);
    Stream<String> learnedAssociations = edge.learnedAssociations().stream()
      .sorted(Comparator.comparing(la -> la.sv.toString()))
      .map(LearnedAssociation::toString);
    return Stream.concat(learnedConstraints, learnedAssociations).collect(Collectors.joining(",\\n"));
  }

  @CheckForNull
  @Override
  public DotGraph.Highlighting highlighting() {
    if (!edge.yields().isEmpty()) {
      return DotGraph.Highlighting.YIELD_EDGE;
    } else if (edge.child.programState.peekValue() instanceof SymbolicValue.ExceptionalSymbolicValue) {
      return DotGraph.Highlighting.EXCEPTION_EDGE;
    }
    return null;
  }

  @Override
  public EdgeDetailsDto details() {
    return details;
  }

  private EdgeDetailsDto buildDetails() {
    return new EdgeDetailsDto(learnedConstraints(), learnedAssociations(), yields());
  }

  private List<SvWithConstraintsDto> learnedConstraints() {
    return edge.learnedConstraints().stream()
      .map(lc -> new SvWithConstraintsDto(lc.sv.toString(), lc.constraint.toString()))
      .sorted()
      .collect(Collectors.toList());
  }

  private List<SvWithSymbolDto> learnedAssociations() {
    return edge.learnedAssociations().stream()
      .map(la -> new SvWithSymbolDto(la.sv.toString(), la.symbol.toString()))
      .sorted()
      .collect(Collectors.toList());
  }

  private List<MethodYieldDto> yields() {
    return edge.yields().stream().map(EGDotNode::yield).collect(Collectors.toList());
  }

}
