package com.knowlink.api.tutors.availability.validations;
import java.util.List;
import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
public interface IAvailabilityBlockValidationService {
    void validateBlocks(List<AvailabilityBlockRequest> blocks);
}