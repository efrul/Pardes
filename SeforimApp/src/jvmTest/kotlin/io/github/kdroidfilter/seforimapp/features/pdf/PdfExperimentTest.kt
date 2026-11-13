package io.github.kdroidfilter.seforimapp.features.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The PDF reader's hidden switch: on only when a list of experiments names it. */
class PdfExperimentTest {
    @Test
    fun `the switch is on when the environment or the property names pdf`() {
        assertTrue(PdfExperiment.listsPdf("pdf", null))
        assertTrue(PdfExperiment.listsPdf(null, "siddur, PDF"))
        assertFalse(PdfExperiment.listsPdf(null, null))
        assertFalse(PdfExperiment.listsPdf("", "pdfs,reader"))
    }

    @Test
    fun `the tool is offered only while the switch is on`() {
        assertEquals(PdfExperiment.enabled, PdfReaderTool.isSupported)
    }
}
