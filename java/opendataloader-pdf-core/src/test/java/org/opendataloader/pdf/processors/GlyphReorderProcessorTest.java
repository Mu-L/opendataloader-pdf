/*
 * Copyright 2025-2026 Hancom Inc.
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
 */
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlyphReorderProcessorTest {

    private TextChunk createTextChunk(String value, double fontSize, List<Double> symbolEnds) {
        BoundingBox bbox = new BoundingBox(0, 0, 100, fontSize);
        TextChunk chunk = new TextChunk(bbox, value, fontSize, 0.0);
        chunk.setSymbolEnds(symbolEnds);
        return chunk;
    }

    @Test
    void testMonotonicSymbolEndsNotDetectedAsGarbled() {
        TextChunk chunk = createTextChunk("abc", 12.0,
            Arrays.asList(10.0, 14.0, 18.0, 22.0));
        assertFalse(GlyphReorderProcessor.hasGarbledSymbolEnds(chunk));
    }

    @Test
    void testSmallInversionBelowThresholdNotDetected() {
        // Slight kerning inversion (0.1 < threshold 12*0.05=0.6)
        TextChunk chunk = createTextChunk("ab", 12.0,
            Arrays.asList(10.0, 14.1, 14.0));
        assertFalse(GlyphReorderProcessor.hasGarbledSymbolEnds(chunk));
    }

    @Test
    void testLargeInversionDetectedAsGarbled() {
        // Large inversion (5.0 > threshold 12*0.05=0.6)
        TextChunk chunk = createTextChunk("ab", 12.0,
            Arrays.asList(10.0, 20.0, 15.0));
        assertTrue(GlyphReorderProcessor.hasGarbledSymbolEnds(chunk));
    }

    @Test
    void testNullSymbolEndsNotGarbled() {
        TextChunk chunk = createTextChunk("ab", 12.0, null);
        chunk.setSymbolEnds(null);
        assertFalse(GlyphReorderProcessor.hasGarbledSymbolEnds(chunk));
    }

    @Test
    void testSingleCharNotGarbled() {
        TextChunk chunk = createTextChunk("a", 12.0, Arrays.asList(10.0, 14.0));
        assertFalse(GlyphReorderProcessor.hasGarbledSymbolEnds(chunk));
    }

    @Test
    void testReorderRestoresCorrectOrder() {
        // Garbled "sk": s spans [18,21], k spans [21,10] (inverted by endX sort)
        // After min/max: s left=18,right=21,mid=19.5; k left=10,right=21,mid=15.5
        // Sorted by midpoint: k(15.5), s(19.5) → "ks"
        TextChunk chunk = createTextChunk("sk", 12.0,
            Arrays.asList(18.0, 21.0, 10.0));
        GlyphReorderProcessor.reorderCharacters(chunk);
        assertEquals("ks", chunk.getValue());
    }

    @Test
    void testReorderPreservesAlreadyCorrectText() {
        TextChunk chunk = createTextChunk("abc", 12.0,
            Arrays.asList(10.0, 14.0, 18.0, 22.0));
        GlyphReorderProcessor.reorderCharacters(chunk);
        assertEquals("abc", chunk.getValue());
    }

    @Test
    void testReorderSkipsWhenSymbolEndsSizeMismatch() {
        TextChunk chunk = createTextChunk("abc", 12.0,
            Arrays.asList(10.0, 14.0));
        GlyphReorderProcessor.reorderCharacters(chunk);
        assertEquals("abc", chunk.getValue());
    }

    @Test
    void testAllSpaceChunkUnchanged() {
        TextChunk chunk = createTextChunk("   ", 12.0,
            Arrays.asList(10.0, 12.0, 14.0, 16.0));
        GlyphReorderProcessor.reorderCharacters(chunk);
        assertEquals("   ", chunk.getValue());
    }

    @Test
    void testFixGarbledTextChunksOnlyModifiesGarbled() {
        TextChunk garbled = createTextChunk("sk", 12.0,
            Arrays.asList(18.0, 21.0, 10.0));
        TextChunk normal = createTextChunk("ab", 12.0,
            Arrays.asList(10.0, 14.0, 18.0));

        List<IObject> contents = new ArrayList<>();
        contents.add(garbled);
        contents.add(normal);

        GlyphReorderProcessor.fixGarbledTextChunks(contents);

        assertEquals("ks", garbled.getValue());
        assertEquals("ab", normal.getValue());
    }
}
