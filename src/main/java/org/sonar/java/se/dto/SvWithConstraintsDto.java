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
package org.sonar.java.se.dto;

import com.google.common.base.Objects;

import org.sonar.java.viewer.dto.CommonDto;

import java.util.Collections;
import java.util.List;

public class SvWithConstraintsDto implements CommonDto, Comparable<SvWithConstraintsDto> {
  public final String sv;
  public final List<String> constraints;

  public SvWithConstraintsDto(String sv, List<String> constraints) {
    this.sv = sv;
    this.constraints = constraints;
  }

  public SvWithConstraintsDto(String sv, String singleConstraint) {
    this.sv = sv;
    this.constraints = Collections.singletonList(singleConstraint);
  }

  @Override
  public int compareTo(SvWithConstraintsDto o) {
    return sv.compareTo(o.sv);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SvWithConstraintsDto)) {
      return false;
    }
    SvWithConstraintsDto that = (SvWithConstraintsDto) obj;
    return Objects.equal(sv, that.sv) && Objects.equal(constraints, that.constraints);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(sv, constraints);
  }
}
