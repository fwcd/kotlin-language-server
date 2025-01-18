package org.javacs.kt

import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import org.javacs.kt.util.Debounce
import java.time.Duration

class DebounceTest {
    val debounce = Debounce(Duration.ofSeconds(1))
    var counter = 0

    @Test fun callQuickly() {
        for (i in 1..10) {
            debounce.schedule {
                counter++
            }
        }

        debounce.waitForPendingTask()

        assertThat(counter, equalTo(1))
    }
}
