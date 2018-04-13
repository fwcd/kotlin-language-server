package org.javacs.kt

import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.junit.Assert.assertThat
import org.junit.Test

class DebounceTest {
    val debounce = Debounce(1.0)
    var counter = 0

    @Test fun callQuickly() {
        for (i in 1..10) {
            debounce.submit {
                counter++
            }
        }

        debounce.waitForPendingTask()

        assertThat(counter, lessThan(3))
    }
}