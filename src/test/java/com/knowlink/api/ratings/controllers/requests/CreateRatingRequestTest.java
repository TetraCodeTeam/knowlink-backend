package com.knowlink.api.ratings.controllers.requests;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateRatingRequestTest {

    @Test
    void trimsCommentAndConvertsBlankToNull() {
        assertThat(new CreateRatingRequest(5, "  buena clase  ").comment()).isEqualTo("buena clase");
        assertThat(new CreateRatingRequest(5, "   ").comment()).isNull();
    }

    @Test
    void rejectsCommentsLongerThanTheAllowedLimit() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();

            assertThat(validator.validate(new CreateRatingRequest(5, "a".repeat(2_001))))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("comment"));
        }
    }
}