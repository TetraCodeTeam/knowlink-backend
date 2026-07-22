package com.knowlink.api.tutors.availability.controllers.implementations;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.availability.controllers.interfaces.ITutorAvailabilityController;
import com.knowlink.api.tutors.availability.controllers.requests.SaveAvailabilityBlocksRequest;
import com.knowlink.api.tutors.availability.controllers.responses.AvailabilityBlockResponse;
import com.knowlink.api.tutors.availability.controllers.responses.WeekCustomizationResponse;
import com.knowlink.api.tutors.availability.services.interfaces.IAvailabilityBlockService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TutorAvailabilityControllerImpl implements ITutorAvailabilityController {

    private final IAvailabilityBlockService availabilityBlockService;

    @Override
    public List<AvailabilityBlockResponse> saveWeekBlocks(
            UserPrincipal principal,
            LocalDate weekStart,
            LocalDate weekEnd,
            SaveAvailabilityBlocksRequest request) {
        return availabilityBlockService.replaceWeekBlocks(
                principal.getUser().getUserId(),
                weekStart,
                weekEnd,
                request.blocks());
    }

    @Override
    public List<AvailabilityBlockResponse> getBlocksInRange(
            UserPrincipal principal,
            LocalDate from,
            LocalDate to) {
        return availabilityBlockService.getBlocksInRange(
                principal.getUser().getUserId(),
                from,
                to);
    }

    @Override
    public WeekCustomizationResponse getWeekCustomization(
            UserPrincipal principal,
            LocalDate weekStart) {
        boolean customized = availabilityBlockService.isWeekCustomized(
                principal.getUser().getUserId(),
                weekStart);
        return new WeekCustomizationResponse(customized);
    }

    @Override
    public void removeWeekCustomization(
            UserPrincipal principal,
            LocalDate weekStart) {
        availabilityBlockService.removeWeekCustomization(
                principal.getUser().getUserId(),
                weekStart);
    }
}