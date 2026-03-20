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
package org.opendataloader.pdf.regression;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for issue #324: Krutidev text extraction with spurious spaces.
 *
 * <p>Krutidev-encoded Hindi text in PDFs (using Walkman-Chanakya Type1 fonts) gets
 * character reordering and spurious space insertion due to verapdf's TextPieceComparator
 * sorting characters by endX coordinate instead of stream order. This causes characters
 * with different widths to be reordered within words.
 *
 * @see <a href="https://github.com/opendataloader-project/opendataloader-pdf/issues/324">Issue #324</a>
 */
class KrutidevSpacingDiagnosticTest {

    @Test
    void testKrutidevTextShouldNotHaveSpuriousSpaces() throws IOException {
        String testPdf = new File(getClass().getClassLoader()
            .getResource("regression/issue324-krutidev.pdf").getFile()).getAbsolutePath();

        Path tempDir = Files.createTempDirectory("krutidev-test");
        try {
            Config config = new Config();
            config.setOutputFolder(tempDir.toString());
            config.setGenerateMarkdown(true);

            DocumentProcessor.processFile(testPdf, config);

            File[] mdFiles = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".md"));
            assertNotNull(mdFiles);
            assertTrue(mdFiles.length > 0, "Should generate markdown output");

            String content = Files.readString(mdFiles[0].toPath());

            // The text should preserve correct Krutidev character order
            // Expected (from pypdf/correct): "yksd lHkk ds izfØ;k vkSj dk;Z lapkyu"
            // Bug (reordered + extra spaces): "ykds lHkk d s ifz Ø;k vkjS dk; Z lpa kyu"
            assertTrue(content.contains("yksd lHkk ds"),
                "Should extract 'yksd lHkk ds' without character reordering or spurious spaces, but got: " + content);
        } finally {
            File[] files = tempDir.toFile().listFiles();
            if (files != null) {
                for (File f : files) {
                    Files.deleteIfExists(f.toPath());
                }
            }
            Files.deleteIfExists(tempDir);
        }
    }
}
