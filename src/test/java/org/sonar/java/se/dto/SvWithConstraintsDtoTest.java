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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SvWithConstraintsDtoTest {

  @Test
  public void test_equals() {
    SvWithConstraintsDto o1 = new SvWithConstraintsDto("sv1", "MY_CONSTRAINT");

    assertThat(o1.equals(null)).isFalse();
    assertThat(o1.equals(new Object())).isFalse();
    assertThat(o1.equals(new SvWithConstraintsDto("sv2", "MY_CONSTRAINT"))).isFalse();
    assertThat(o1.equals(new SvWithConstraintsDto("sv1", "OTHER_CONSTRAINT"))).isFalse();

    assertThat(o1.equals(o1)).isTrue();
    assertThat(o1.equals(new SvWithConstraintsDto("sv1", "MY_CONSTRAINT"))).isTrue();
  }

  @Test
  public void test_hashCode() {
    SvWithConstraintsDto o1 = new SvWithConstraintsDto("sv1", "MY_CONSTRAINT");

    assertThat(o1.hashCode()).isEqualTo(o1.hashCode());
    assertThat(o1.hashCode()).isEqualTo(new SvWithConstraintsDto("sv1", "MY_CONSTRAINT").hashCode());
    assertThat(o1.hashCode()).isNotEqualTo(new SvWithConstraintsDto("sv2", "MY_CONSTRAINT").hashCode());
    assertThat(o1.hashCode()).isNotEqualTo(new SvWithConstraintsDto("sv1", "OTHER_CONSTRAINT").hashCode());
  }

}
