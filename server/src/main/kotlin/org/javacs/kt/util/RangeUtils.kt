package org.javacs.kt.util

import org.eclipse.lsp4j.Range

// checks if the current range is within the other range (same lines, within the character bounds)
fun Range.isSubrangeOf(otherRange: Range): Boolean =
    otherRange.start.line == this.start.line && otherRange.end.line == this.end.line &&
        otherRange.start.character <= this.start.character && otherRange.end.character >= this.end.character
