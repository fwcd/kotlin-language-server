package org.javacs.kt

import com.google.common.collect.ImmutableMap
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.util.slicedMap.ReadOnlySlice
import org.jetbrains.kotlin.util.slicedMap.WritableSlice

object DummyTrace: BindingTrace {
    override fun report(diagnostic: Diagnostic) {
    }

    override fun <K : Any?, V : Any?> getKeys(slice: WritableSlice<K, V>?): MutableCollection<K> =
            mutableSetOf()

    override fun getBindingContext(): BindingContext {
        return object: BindingContext {
            override fun <K : Any?, V : Any?> getKeys(slice: WritableSlice<K, V>?): Collection<K> =
                    DummyTrace.getKeys(slice)

            override fun getType(expr: KtExpression): KotlinType? = DummyTrace.getType(expr)

            override fun <K : Any?, V : Any?> get(slice: ReadOnlySlice<K, V>?, key: K): V? = DummyTrace.get(slice, key)

            override fun getDiagnostics(): Diagnostics = Diagnostics.EMPTY

            override fun addOwnDataTo(trace: BindingTrace, commitDiagnostics: Boolean) {
                // do nothing
            }

            override fun <K : Any?, V : Any?> getSliceContents(slice: ReadOnlySlice<K, V>): ImmutableMap<K, V> =
                    ImmutableMap.of()
        }
    }

    override fun <K : Any?, V : Any?> record(slice: WritableSlice<K, V>?, key: K, value: V) {
    }

    override fun <K : Any?> record(slice: WritableSlice<K, Boolean>?, key: K) {
    }

    override fun getType(expr: KtExpression): KotlinType? = null

    override fun wantsDiagnostics(): Boolean = true

    override fun <K : Any?, V : Any?> get(slice: ReadOnlySlice<K, V>?, key: K): V? = null

    override fun recordType(expr: KtExpression, type: KotlinType?) {
    }
}