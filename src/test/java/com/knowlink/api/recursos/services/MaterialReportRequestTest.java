package com.knowlink.api.recursos.services;

import com.knowlink.api.materials.controller.requests.MaterialReportRequest;
import com.knowlink.api.materials.data.enums.MaterialReportReason;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialReportRequestTest {

    @Test
    void reasonIsRequiredButDescriptionIsOptional() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();

            assertThat(validator.validate(new MaterialReportRequest(MaterialReportReason.OTHER, null))).isEmpty();
            assertThat(validator.validate(new MaterialReportRequest(null, null)))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("reason");
        }
    }

    @Test
    void descriptionIsTrimmedAndCannotExceedLimit() {
        assertThat(new MaterialReportRequest(MaterialReportReason.OTHER, "  detalle  ").description())
                .isEqualTo("detalle");
        assertThat(new MaterialReportRequest(MaterialReportReason.OTHER, "   ").description()).isNull();

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            assertThat(validator.validate(new MaterialReportRequest(MaterialReportReason.OTHER, "a".repeat(2001))))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("description");
        }
    }
}