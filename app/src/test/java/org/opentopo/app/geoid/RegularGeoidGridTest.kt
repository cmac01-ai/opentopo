package org.opentopo.app.geoid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class RegularGeoidGridTest {

    @Test
    fun `loads IBGE style grid and interpolates cubic spline`() {
        val file = File.createTempFile("geoid", ".txt")
        file.writeText(
            """
            285.0  6.0  10.0
            286.0  6.0  20.0
            285.0  5.0  30.0
            286.0  5.0  40.0
            """.trimIndent()
        )

        val grid = RegularGeoidGrid.load(file)

        assertEquals(10.0, grid.interpolate(6.0, -75.0)!!, 1e-6)
        assertEquals(25.0, grid.interpolate(5.5, -74.5)!!, 1e-6)
        assertEquals(40.0, grid.interpolate(5.0, -74.0)!!, 1e-6)
        assertNull(grid.interpolate(7.0, -74.5))

        file.delete()
    }
}
