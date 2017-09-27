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

public class SvWithSymbolDtoTest {

  @Test
  public void test_equals() {
    SvWithSymbolDto o1 = new SvWithSymbolDto("sv1", "a");

    assertThat(o1.equals(null)).isFalse();
    assertThat(o1.equals(new Object())).isFalse();
    assertThat(o1.equals(new SvWithSymbolDto("sv2", "a"))).isFalse();
    assertThat(o1.equals(new SvWithSymbolDto("sv1", "b"))).isFalse();

    assertThat(o1.equals(o1)).isTrue();
    assertThat(o1.equals(new SvWithSymbolDto("sv1", "a"))).isTrue();
  }

  @Test
  public void test_hashCode() {
    SvWithSymbolDto o1 = new SvWithSymbolDto("sv1", "a");

    assertThat(o1.hashCode()).isEqualTo(o1.hashCode());
    assertThat(o1.hashCode()).isEqualTo(new SvWithSymbolDto("sv1", "a").hashCode());
    assertThat(o1.hashCode()).isNotEqualTo(new SvWithSymbolDto("sv2", "a").hashCode());
    assertThat(o1.hashCode()).isNotEqualTo(new SvWithSymbolDto("sv1", "b").hashCode());
  }

}
