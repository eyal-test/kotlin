/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("DEPRECATION")

package kotlinx.metadata.jvm

import kotlinx.metadata.Flag
import kotlinx.metadata.internal.FlagImpl
import org.jetbrains.kotlin.metadata.deserialization.Flags as F
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmFlags as JF

private const val prefix = "Flag API is deprecated. Please use"

/**
 * JVM-specific flags in addition to common flags declared in [Flag].
 *
 * @see Flag
 * @see Flags
 */
@Deprecated("$prefix corresponding extensions on Km nodes, such as KmClass.hasMethodBodiesInInterface")
object JvmFlag {
    /**
     * JVM-specific property flags in addition to common property flags declared in [Flag.Property].
     */
    @Deprecated("$prefix corresponding extension on KmProperty: KmProperty.isMovedFromInterfaceCompanion")
    object Property {
        /**
         * Applied to a property declared in an interface's companion object, signifies that its backing field is declared as a static
         * field in the interface. In Kotlin code, this usually happens if the property is annotated with [JvmField].
         *
         * Has no effect if the property is not declared in a companion object of some interface.
         */
        @JvmField
        @Deprecated("$prefix KmProperty.isMovedFromInterfaceCompanion")
        val IS_MOVED_FROM_INTERFACE_COMPANION = booleanFlag(JF.IS_MOVED_FROM_INTERFACE_COMPANION)
    }

    /**
     * JVM-specific class flags in addition to common class flags declared in [Flag.Class].
     */
    @Deprecated("$prefix corresponding extensions on KmClass")
    object Class {
        /**
         * Applied to an interface compiled with -Xjvm-default=all or all-compatibility.
         *
         * Without this flag or a `@JvmDefault` annotation on individual interface methods
         * the Kotlin compiler moves all interface method bodies into a nested `DefaultImpls`
         * class.
         */
        @JvmField
        @Deprecated("$prefix KmClass.hasMethodBodiesInInterface")
        val HAS_METHOD_BODIES_IN_INTERFACE = booleanFlag(JF.IS_COMPILED_IN_JVM_DEFAULT_MODE)

        /**
         * Applied to an interface compiled with -Xjvm-default=all-compatibility.
         *
         * In compatibility mode we generate method bodies directly in the interface,
         * but we also generate bridges in a nested `DefaultImpls` class for use by
         * clients compiled without all-compatibility.
         */
        @JvmField
        @Deprecated("$prefix KmClass.isCompiledInCompatibilityMode")
        val IS_COMPILED_IN_COMPATIBILITY_MODE = booleanFlag(JF.IS_COMPILED_IN_COMPATIBILITY_MODE)
    }

    internal fun booleanFlag(f: F.BooleanFlagField): Flag =
        FlagImpl(f.offset, f.bitWidth, 1)
}
