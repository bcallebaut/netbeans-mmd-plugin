/*
 * Copyright 2015 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.igormaznitsa.nbmindmap.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class SourcePositionTest {
  
  @Test
  public void testConstructorAllParts() {
    final SourcePosition pos = new SourcePosition("test.java",2386482,23741);
    assertEquals("test.java",pos.getSourceName());
    assertEquals(2386482, pos.getLine());
    assertEquals(23741, pos.getPosition());
  }

  @Test
  public void testConstructorPacked() {
    final SourcePosition pos = new SourcePosition("test.java[2386482:23741]");
    assertEquals("test.java",pos.getSourceName());
    assertEquals(2386482, pos.getLine());
    assertEquals(23741, pos.getPosition());
    assertEquals("test.java[2386482:23741]",pos.toString());
  }

  @Test
  public void testToString(){
    assertEquals("test.java[2386482:23741]", new SourcePosition("test.java[2386482:23741]").toString());
    assertEquals("[-1:-2]", new SourcePosition("[-1:-2]").toString());
    assertEquals("[-1:-2]", new SourcePosition(null, -1, -2).toString());
  }
  
}