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

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fixes character reordering caused by verapdf's TextPieceComparator sorting characters
 * by endX coordinate instead of stream order. This affects fonts with non-uniform glyph
 * widths (e.g., Krutidev/Walkman-Chanakya Hindi fonts) where the endX-based sort produces
 * incorrect character ordering within text chunks.
 *
 * <p>Detection: a TextChunk's symbolEnds list should be monotonically non-decreasing.
 * When symbolEnds[i+1] &lt; symbolEnds[i], the character at position i has been displaced.
 *
 * <p>Fix: strip spaces, reorder non-space characters by glyph midpoint
 * (start + end) / 2, then re-insert spaces at positions where gaps in the
 * reconstructed symbolEnds exceed a threshold proportional to font size.
 *
 * @see <a href="https://github.com/opendataloader-project/opendataloader-pdf/issues/324">Issue #324</a>
 */
public class GlyphReorderProcessor {

    private static final Logger LOGGER = Logger.getLogger(GlyphReorderProcessor.class.getCanonicalName());
    private static final double SPACE_GAP_RATIO = 0.17;
    private static final double INVERSION_THRESHOLD_RATIO = 0.05;

    /**
     * Detects and fixes garbled character ordering in text chunks.
     *
     * @param contents the page contents to process
     */
    public static void fixGarbledTextChunks(List<IObject> contents) {
        for (int i = 0; i < contents.size(); i++) {
            IObject content = contents.get(i);
            if (content instanceof TextChunk) {
                TextChunk textChunk = (TextChunk) content;
                if (hasGarbledSymbolEnds(textChunk)) {
                    reorderCharacters(textChunk);
                }
            }
        }
    }

    static boolean hasGarbledSymbolEnds(TextChunk textChunk) {
        List<Double> symbolEnds = textChunk.getSymbolEnds();
        if (symbolEnds == null || symbolEnds.size() < 2) {
            return false;
        }
        double threshold = textChunk.getFontSize() * INVERSION_THRESHOLD_RATIO;
        for (int i = 0; i < symbolEnds.size() - 1; i++) {
            if (symbolEnds.get(i) - symbolEnds.get(i + 1) > threshold) {
                return true;
            }
        }
        return false;
    }

    static void reorderCharacters(TextChunk textChunk) {
        String value = textChunk.getValue();
        List<Double> symbolEnds = textChunk.getSymbolEnds();
        if (symbolEnds == null || symbolEnds.size() != value.length() + 1) {
            return;
        }

        // Step 1: Collect non-space characters with position data
        List<CharWithPosition> nonSpaceChars = new ArrayList<>();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isSpaceChar(ch)) {
                double start = symbolEnds.get(i);
                double end = symbolEnds.get(i + 1);
                double midpoint = (start + end) / 2.0;
                nonSpaceChars.add(new CharWithPosition(ch, midpoint, Math.min(start, end), Math.max(start, end)));
            }
        }

        if (nonSpaceChars.isEmpty()) {
            return;
        }

        // Step 2: Sort by glyph midpoint to restore correct character order
        nonSpaceChars.sort(Comparator.comparingDouble(c -> c.midpoint));

        // Step 3: Re-insert spaces where gaps exceed threshold
        double spaceThreshold = textChunk.getFontSize() * SPACE_GAP_RATIO;
        StringBuilder result = new StringBuilder(nonSpaceChars.size() + 10);
        List<Double> newSymbolEnds = new ArrayList<>(nonSpaceChars.size() + 11);

        CharWithPosition first = nonSpaceChars.get(0);
        result.append(first.ch);
        newSymbolEnds.add(first.leftEdge);
        newSymbolEnds.add(first.rightEdge);

        for (int i = 1; i < nonSpaceChars.size(); i++) {
            CharWithPosition prev = nonSpaceChars.get(i - 1);
            CharWithPosition curr = nonSpaceChars.get(i);
            double gap = curr.leftEdge - prev.rightEdge;
            if (gap > spaceThreshold) {
                result.append(' ');
                newSymbolEnds.add(curr.leftEdge);
            }
            result.append(curr.ch);
            newSymbolEnds.add(curr.rightEdge);
        }

        String newValue = result.toString();
        if (newValue.equals(value)) {
            return;
        }

        LOGGER.log(Level.FINE, "Reordered garbled text chunk: [{0}] -> [{1}]",
            new Object[]{value, newValue});

        textChunk.setValue(newValue);
        textChunk.adjustSymbolEndsToBoundingBox(newSymbolEnds);
    }

    private static class CharWithPosition {
        final char ch;
        final double midpoint;
        final double leftEdge;
        final double rightEdge;

        CharWithPosition(char ch, double midpoint, double leftEdge, double rightEdge) {
            this.ch = ch;
            this.midpoint = midpoint;
            this.leftEdge = leftEdge;
            this.rightEdge = rightEdge;
        }
    }
}
