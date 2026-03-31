/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @library /test/lib /
 * @summary x64 AVX-512: emit KNOT for shared VectorMask.not() on int and long masks
 * @modules jdk.incubator.vector
 * @requires os.simpleArch == "x64" & vm.compiler2.enabled & vm.cpu.features ~= ".*avx512dq.*"
 *
 * @run driver compiler.vectorapi.VectorMaskNotKnotTest
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

public class VectorMaskNotKnotTest {
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_512;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_512;
    private static final VectorSpecies<Integer> I_LT8_SPECIES = IntVector.SPECIES_128;
    private static final VectorSpecies<Long> L_LEN2_SPECIES = LongVector.SPECIES_128;
    // Use 256-bit long vectors (4 lanes) to exercise the x86 LT8 mask_not path
    // in this test. The 128-bit long shape is covered separately for correctness.
    private static final VectorSpecies<Long> L_LT8_SPECIES = LongVector.SPECIES_256;
    private static final int LENGTH = 1024;

    private static final int[] INT_DATA = new int[LENGTH];
    private static final long[] LONG_DATA = new long[LENGTH];

    private static volatile int intResult;
    private static volatile int longResult;
    private static volatile int longLen2Result;
    private static volatile int intLt8Result;
    private static volatile int longLt8Result;

    static {
        for (int i = 0; i < LENGTH; i++) {
            int value = i % 10;
            INT_DATA[i] = value;
            LONG_DATA[i] = value;
        }
    }

    private static int expectedIntCount(VectorSpecies<Integer> species, int first, int second) {
        int count = 0;
        for (int i = 0; i < LENGTH; i += species.length()) {
            int trueCount = 0;
            for (int j = 0; j < species.length(); j++) {
                int value = INT_DATA[i + j];
                if (value != first && value != second) {
                    trueCount++;
                }
            }
            count += trueCount;
        }
        return count;
    }

    private static int expectedLongCount(VectorSpecies<Long> species, long first, long second) {
        int count = 0;
        for (int i = 0; i < LENGTH; i += species.length()) {
            int trueCount = 0;
            for (int j = 0; j < species.length(); j++) {
                long value = LONG_DATA[i + j];
                if (value != first && value != second) {
                    trueCount++;
                }
            }
            count += trueCount;
        }
        return count;
    }

    private static int expectedIntMaskNot(VectorSpecies<Integer> species) {
        return expectedIntCount(species, 3, 7) + expectedIntCount(species, 2, 9);
    }

    private static int expectedLongMaskNot(VectorSpecies<Long> species) {
        return expectedLongCount(species, 3L, 7L) + expectedLongCount(species, 2L, 9L);
    }

    private static void assertIntMaskNotResult(VectorSpecies<Integer> species, int actual) {
        Asserts.assertEquals(expectedIntMaskNot(species), actual);
    }

    private static void assertLongMaskNotResult(VectorSpecies<Long> species, int actual) {
        Asserts.assertEquals(expectedLongMaskNot(species), actual);
    }

    @ForceInline
    private static int intMaskNotKernel(VectorSpecies<Integer> species) {
        int sum = 0;
        for (int i = 0; i < LENGTH; i += species.length()) {
            IntVector vector = IntVector.fromArray(species, INT_DATA, i);
            VectorMask<Integer> exclude37 = vector.compare(VectorOperators.EQ, 3)
                                                 .or(vector.compare(VectorOperators.EQ, 7));
            VectorMask<Integer> exclude29 = vector.compare(VectorOperators.EQ, 2)
                                                 .or(vector.compare(VectorOperators.EQ, 9));
            sum += exclude37.not().trueCount();
            sum += exclude29.not().trueCount();
        }
        return sum;
    }

    @ForceInline
    private static int longMaskNotKernel(VectorSpecies<Long> species) {
        int sum = 0;
        for (int i = 0; i < LENGTH; i += species.length()) {
            LongVector vector = LongVector.fromArray(species, LONG_DATA, i);
            VectorMask<Long> exclude37 = vector.compare(VectorOperators.EQ, 3L)
                                              .or(vector.compare(VectorOperators.EQ, 7L));
            VectorMask<Long> exclude29 = vector.compare(VectorOperators.EQ, 2L)
                                              .or(vector.compare(VectorOperators.EQ, 9L));
            sum += exclude37.not().trueCount();
            sum += exclude29.not().trueCount();
        }
        return sum;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = { "\\bmask_not\\b", ">= 2" },
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testIntMaskNot() {
        intResult = intMaskNotKernel(I_SPECIES);
    }

    @Run(test = "testIntMaskNot")
    public static void testIntMaskNot_runner() {
        testIntMaskNot();
        assertIntMaskNotResult(I_SPECIES, intResult);
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = { "\\bmask_not\\b", ">= 2" },
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testLongMaskNot() {
        longResult = longMaskNotKernel(L_SPECIES);
    }

    @Run(test = "testLongMaskNot")
    public static void testLongMaskNot_runner() {
        testLongMaskNot();
        assertLongMaskNotResult(L_SPECIES, longResult);
    }

    @Test(compLevel = CompLevel.C2)
    public static void testLongMaskNotLen2() {
        longLen2Result = longMaskNotKernel(L_LEN2_SPECIES);
    }

    @Run(test = "testLongMaskNotLen2")
    public static void testLongMaskNotLen2_runner() {
        testLongMaskNotLen2();
        assertLongMaskNotResult(L_LEN2_SPECIES, longLen2Result);
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = { "\\bmask_not_LT8\\b", ">= 2" },
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testIntMaskNotLT8() {
        intLt8Result = intMaskNotKernel(I_LT8_SPECIES);
    }

    @Run(test = "testIntMaskNotLT8")
    public static void testIntMaskNotLT8_runner() {
        testIntMaskNotLT8();
        assertIntMaskNotResult(I_LT8_SPECIES, intLt8Result);
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = { "\\bmask_not_LT8\\b", ">= 2" },
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testLongMaskNotLT8() {
        longLt8Result = longMaskNotKernel(L_LT8_SPECIES);
    }

    @Run(test = "testLongMaskNotLT8")
    public static void testLongMaskNotLT8_runner() {
        testLongMaskNotLT8();
        assertLongMaskNotResult(L_LT8_SPECIES, longLt8Result);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .addFlags("-XX:UseAVX=3")
                     .addFlags("-XX:MaxVectorSize=64")
                     .start();
    }
}
