package com.urlshortener.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ShortCodeGenerator}.
 * No Spring context needed — pure unit test.
 */
class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator(7);

    @Test
    void generatedCode_hasCorrectLength() {
        String code = generator.generate();
        assertThat(code).hasSize(7);
    }

    @Test
    void generatedCode_containsOnlyBase62Characters() {
        for (int i = 0; i < 500; i++) {
            String code = generator.generate();
            assertThat(code).matches("[A-Za-z0-9]+");
        }
    }

    @RepeatedTest(3)
    void generatedCodes_areNotAllIdentical_overLargeSample() {
        // With 62^7 code-space, generating 1000 codes should yield near-unique results
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generate());
        }
        // Expect virtually no collisions in 1000 draws from a 3.5-trillion space
        assertThat(codes.size()).isGreaterThan(990);
    }

    @Test
    void constructor_rejectsLengthBelowMinimum() {
        assertThatThrownBy(() -> new ShortCodeGenerator(3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 4 and 16");
    }

    @Test
    void constructor_rejectsLengthAboveMaximum() {
        assertThatThrownBy(() -> new ShortCodeGenerator(17))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 4 and 16");
    }

    @Test
    void isValidAlias_acceptsAlphanumericWithDashUnderscore() {
        assertThat(generator.isValidAlias("my-link_2024")).isTrue();
        assertThat(generator.isValidAlias("abc123")).isTrue();
    }

    @Test
    void isValidAlias_rejectsSpecialCharacters() {
        assertThat(generator.isValidAlias("my link")).isFalse();  // space
        assertThat(generator.isValidAlias("link!")).isFalse();     // !
        assertThat(generator.isValidAlias("li/nk")).isFalse();     // slash
    }

    @Test
    void isValidAlias_rejectsNullAndBlank() {
        assertThat(generator.isValidAlias(null)).isFalse();
        assertThat(generator.isValidAlias("")).isFalse();
        assertThat(generator.isValidAlias("   ")).isFalse();
    }

    @Test
    void isValidAlias_rejectsTooLong() {
        assertThat(generator.isValidAlias("a".repeat(17))).isFalse();
    }
}
