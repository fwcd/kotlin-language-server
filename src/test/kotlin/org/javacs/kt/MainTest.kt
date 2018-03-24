package org.javacs.kt

import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class MainTest {
    @Test
    fun `message() should return "Hello World!"`() {
        assertThat(message(), equalTo("Hello world!"))
    }
}