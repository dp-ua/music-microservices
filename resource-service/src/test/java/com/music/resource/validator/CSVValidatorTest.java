package com.music.resource.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.music.resource.exception.InvalidIdException;

class CSVValidatorTest {

    private final CSVValidator validator = new CSVValidator();

    @Test
    @DisplayName("A single id is parsed")
    void parsesSingleId() {
        assertThat(validator.parseAndValidateIds("5")).containsExactly(5L);
    }

    @Test
    @DisplayName("A comma separated list is parsed, the order is kept")
    void parsesListOfIds() {
        assertThat(validator.parseAndValidateIds("1,2,3")).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("Spaces around the numbers are ignored")
    void ignoresSpacesAroundNumbers() {
        assertThat(validator.parseAndValidateIds(" 1 , 22 ,333 ")).containsExactly(1L, 22L, 333L);
    }

    @ParameterizedTest(name = "[{0}] is rejected as empty")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void blankValueIsRejected(String ids) {
        assertThatThrownBy(() -> validator.parseAndValidateIds(ids))
                .isInstanceOf(InvalidIdException.class)
                .hasMessage("IDs cannot be empty");
    }

    @ParameterizedTest(name = "[{0}] is rejected as an invalid id")
    @ValueSource(strings = {"abc", "1,abc", "1.5", "0", "-1", "1,0", "1,-5", "9223372036854775808"})
    void notPositiveIntegerIsRejected(String ids) {
        assertThatThrownBy(() -> validator.parseAndValidateIds(ids))
                .isInstanceOf(InvalidIdException.class)
                .hasMessageContaining("Invalid ID format");
    }

    @Test
    @DisplayName("The message names the first invalid value")
    void messageNamesInvalidValue() {
        assertThatThrownBy(() -> validator.parseAndValidateIds("1,2,x7"))
                .isInstanceOf(InvalidIdException.class)
                .hasMessageContaining("'x7'");
    }

    @Test
    @DisplayName("200 characters are allowed")
    void maximumLengthIsAllowed() {
        var ids = "1,".repeat(100);
        assertThat(ids).hasSize(200);

        assertThat(validator.parseAndValidateIds(ids)).hasSize(100);
    }

    @Test
    @DisplayName("More than 200 characters are rejected")
    void tooLongStringIsRejected() {
        var ids = LongStream.rangeClosed(1, 100).mapToObj(Long::toString).collect(Collectors.joining(","));
        assertThat(ids.length()).isGreaterThan(200);

        assertThatThrownBy(() -> validator.parseAndValidateIds(ids))
                .isInstanceOf(InvalidIdException.class)
                .hasMessageContaining("too long");
    }

    @Test
    @DisplayName("The error code of the validation errors is 400")
    void errorCodeIs400() {
        assertThatThrownBy(() -> validator.parseAndValidateIds("abc"))
                .isInstanceOfSatisfying(InvalidIdException.class, e -> assertThat(e.getErrorCode()).isEqualTo("400"));
    }

    @Test
    @DisplayName("The result is a list of Long values")
    void resultIsListOfLong() {
        List<Long> result = validator.parseAndValidateIds("10,20");

        assertThat(result).containsExactly(10L, 20L);
    }

}
