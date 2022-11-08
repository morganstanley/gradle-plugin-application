/*
 * Morgan Stanley makes this available to you under the Apache License, Version 2.0 (the "License"). You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.ms.gradle.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

@SuppressWarnings({"ConstantConditions", "ObviousNullCheck"})
class UtilsTest {

    @Test
    void testArgument() {
        Class<?> classObj = getClass();
        Utils.argument(true, "Error in %s!", classObj);
        Assertions.assertThatThrownBy(() -> Utils.argument(false, "Error in %s!", classObj))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Error in %s!", classObj);
    }

    @Test
    void testNonNull() {
        Utils.nonNull(this, "myValue");
        Assertions.assertThatThrownBy(() -> Utils.nonNull(null, "myValue"))
                .isInstanceOf(NullPointerException.class).hasMessage("myValue must not be null");
    }

    @Test
    void testNonEmpty() {
        Utils.nonEmpty("TEST", "myValue");
        Assertions.assertThatThrownBy(() -> Utils.nonEmpty("", "myValue"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("myValue must not be empty");
        Assertions.assertThatThrownBy(() -> Utils.nonEmpty(null, "myValue"))
                .isInstanceOf(NullPointerException.class).hasMessage("myValue must not be null");
    }

    @Test
    void testNonNullElements() {
        Utils.nonNullElements(Arrays.asList("TEST", ""), "myList");
        Utils.nonNullElements(Collections.singletonList("TEST"), "myList");
        Utils.nonNullElements(Collections.singletonList(""), "myList");
        Utils.nonNullElements(Collections.emptyList(), "myList");
        Assertions.assertThatThrownBy(() -> Utils.nonNullElements(Arrays.asList("TEST", null, ""), "myList"))
                .isInstanceOf(NullPointerException.class).hasMessage("Elements of myList must not be null");
        Assertions.assertThatThrownBy(() -> Utils.nonNullElements(Arrays.asList("TEST", null), "myList"))
                .isInstanceOf(NullPointerException.class).hasMessage("Elements of myList must not be null");
        Assertions.assertThatThrownBy(() -> Utils.nonNullElements(Collections.singletonList(null), "myList"))
                .isInstanceOf(NullPointerException.class).hasMessage("Elements of myList must not be null");
        Assertions.assertThatThrownBy(() -> Utils.nonNullElements(null, "myList"))
                .isInstanceOf(NullPointerException.class).hasMessage("myList must not be null");
    }

    @Test
    void testCapitalize() {
        Assertions.assertThat(Utils.capitalize("")).isEqualTo("");
        Assertions.assertThat(Utils.capitalize("t")).isEqualTo("T");
        Assertions.assertThat(Utils.capitalize("T")).isEqualTo("T");

        Assertions.assertThat(Utils.capitalize("test")).isEqualTo("Test");
        Assertions.assertThat(Utils.capitalize("Test")).isEqualTo("Test");
        Assertions.assertThat(Utils.capitalize("tEST")).isEqualTo("TEST");
        Assertions.assertThat(Utils.capitalize("TEST")).isEqualTo("TEST");

        Assertions.assertThat(Utils.capitalize("testValue")).isEqualTo("TestValue");
        Assertions.assertThat(Utils.capitalize("test.value")).isEqualTo("Test.value");
        Assertions.assertThat(Utils.capitalize("test-value")).isEqualTo("Test-value");
        Assertions.assertThat(Utils.capitalize("test_value")).isEqualTo("Test_value");

        Assertions.assertThat(Utils.capitalize(".test.value")).isEqualTo(".test.value");
        Assertions.assertThat(Utils.capitalize("-test-value")).isEqualTo("-test-value");
        Assertions.assertThat(Utils.capitalize("_test_value")).isEqualTo("_test_value");

        Assertions.assertThat(Utils.capitalize(".42")).isEqualTo(".42");
        Assertions.assertThat(Utils.capitalize("3.14")).isEqualTo("3.14");
        Assertions.assertThat(Utils.capitalize("-3.14")).isEqualTo("-3.14");

        Assertions.assertThat(Utils.capitalize("árvízTűrő")).isEqualTo("ÁrvízTűrő");
        Assertions.assertThat(Utils.capitalize("ÁrvízTűrő")).isEqualTo("ÁrvízTűrő");
        Assertions.assertThat(Utils.capitalize("tükörFúróGép")).isEqualTo("TükörFúróGép");
        Assertions.assertThat(Utils.capitalize("TükörFúróGép")).isEqualTo("TükörFúróGép");
        Assertions.assertThat(Utils.capitalize("őrTűz")).isEqualTo("ŐrTűz");
        Assertions.assertThat(Utils.capitalize("ŐrTűz")).isEqualTo("ŐrTűz");
    }
}
