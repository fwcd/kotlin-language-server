package org.javacs.kt

import org.hamcrest.Matchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.javacs.kt.util.Debouncer
import java.time.Duration

class DebouncerTest {
    val debouncer = Debouncer(Duration.ofSeconds(1))
    var counter = 0

    @Test fun callQuickly() {
        for (i in 1..10) {
            debouncer.schedule {
                counter++
            }
        }

        debouncer.waitForPendingTask()

        assertThat(counter, equalTo(1))
    }
}
