package com.knowlink.api.tutors.availability.services.implementations;

import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
import com.knowlink.api.tutors.availability.controllers.responses.AvailabilityBlockResponse;
import com.knowlink.api.tutors.availability.data.mappers.AvailabilityBlockMapper;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;
import com.knowlink.api.tutors.availability.data.models.AvailabilityWeekCustomization;
import com.knowlink.api.shared.utils.PastTimeUtil;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.timeslot.data.mappers.TimeSlotGenerator;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityWeekCustomizationRepository;
import com.knowlink.api.tutors.availability.services.interfaces.IAvailabilityBlockService;
import com.knowlink.api.tutors.availability.utils.AvailabilityConstants;
import com.knowlink.api.tutors.availability.validations.IAvailabilityBlockValidationService;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvailabilityBlockServiceImpl implements IAvailabilityBlockService {

    private final IAvailabilityBlockRepository availabilityBlockRepository;
    private final IAvailabilityWeekCustomizationRepository weekCustomizationRepository;
    private final ITutorProfileValidationService tutorProfileValidationService;
    private final IAvailabilityBlockValidationService availabilityBlockValidationService;
    private final AvailabilityBlockMapper availabilityBlockMapper;
    private final ITimeSlotRepository timeSlotRepository;
    private final TimeSlotGenerator timeSlotGenerator;

    @Override
    @Transactional
    public List<AvailabilityBlockResponse> replaceWeekBlocks(
            UUID tutorUserId,
            LocalDate weekStart,
            LocalDate weekEnd,
            List<AvailabilityBlockRequest> blocks) {

        availabilityBlockValidationService.validateWeekRequest(weekStart, weekEnd, blocks);
        availabilityBlockValidationService.validateBlocks(blocks);
        availabilityBlockValidationService.validateNoActiveBookingsInRange(tutorUserId, weekStart, weekEnd);

        TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
        UUID tutorProfileId = tutorProfile.getTutorProfileId();

        boolean anyRepeat = blocks.stream().anyMatch(request -> Boolean.TRUE.equals(request.repeatWeekly()));

        List<Integer> protectedWeekOffsets = anyRepeat
                ? clearNonProtectedFutureWeeks(tutorUserId, tutorProfileId, weekStart, weekEnd)
                : List.of();

        timeSlotRepository.deleteAvailableInRange(tutorProfileId, weekStart, weekEnd);
        availabilityBlockRepository.deleteInRange(tutorProfileId, weekStart, weekEnd);

        List<AvailabilityBlock> toSave = buildBlocksToSave(blocks, tutorProfile, protectedWeekOffsets);
        availabilityBlockRepository.saveAll(toSave);

        List<TimeSlot> newSlots = toSave.stream()
                .flatMap(block -> timeSlotGenerator.generate(block, block.getDate(), block.getDate()).stream())
                .toList();
        timeSlotRepository.saveAll(newSlots);

        markWeekAsCustomized(tutorProfile, weekStart);

        return findResponsesInRange(tutorProfileId, weekStart, weekEnd);
    }

    private List<Integer> clearNonProtectedFutureWeeks(
            UUID tutorUserId, UUID tutorProfileId, LocalDate weekStart, LocalDate weekEnd) {
        List<Integer> protectedWeekOffsets = new ArrayList<>();

        for (int i = 1; i <= AvailabilityConstants.REPEAT_WEEKS_AHEAD; i++) {
            LocalDate futureWeekStart = weekStart.plusWeeks(i);
            LocalDate futureWeekEnd = weekEnd.plusWeeks(i);

            boolean isCustomized = weekCustomizationRepository
                    .existsByTutorProfile_TutorProfileIdAndWeekStart(tutorProfileId, futureWeekStart);

            if (isCustomized) {
                protectedWeekOffsets.add(i);
                continue;
            }

            availabilityBlockValidationService.validateNoActiveBookingsInRange(
                    tutorUserId, futureWeekStart, futureWeekEnd);

            timeSlotRepository.deleteAvailableInRange(tutorProfileId, futureWeekStart, futureWeekEnd);
            availabilityBlockRepository.deleteInRange(tutorProfileId, futureWeekStart, futureWeekEnd);
        }

        return protectedWeekOffsets;
    }

    private List<AvailabilityBlock> buildBlocksToSave(
            List<AvailabilityBlockRequest> blocks, TutorProfile tutorProfile, List<Integer> protectedWeekOffsets) {

        List<AvailabilityBlock> toSave = new ArrayList<>();

        for (AvailabilityBlockRequest request : blocks) {
            AvailabilityBlock origin = availabilityBlockMapper.toEntity(request, tutorProfile);
            toSave.add(origin);

            if (Boolean.TRUE.equals(request.repeatWeekly())) {
                for (int i = 1; i <= AvailabilityConstants.REPEAT_WEEKS_AHEAD; i++) {
                    if (protectedWeekOffsets.contains(i)) {
                        continue;
                    }
                    LocalDate futureDate = request.date().plusWeeks(i);
                    toSave.add(availabilityBlockMapper.toGeneratedCopy(origin, futureDate));
                }
            }
        }

        return toSave;
    }

    private void markWeekAsCustomized(TutorProfile tutorProfile, LocalDate weekStart) {
        boolean alreadyMarked = weekCustomizationRepository.existsByTutorProfile_TutorProfileIdAndWeekStart(
                tutorProfile.getTutorProfileId(), weekStart);

        if (!alreadyMarked) {
            weekCustomizationRepository.save(AvailabilityWeekCustomization.builder()
                    .weekStart(weekStart)
                    .tutorProfile(tutorProfile)
                    .build());
        }
    }

    @Override
    @Transactional
    public void removeWeekCustomization(UUID tutorUserId, LocalDate weekStart) {
        TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
        weekCustomizationRepository.deleteByTutorProfile_TutorProfileIdAndWeekStart(
                tutorProfile.getTutorProfileId(), weekStart);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWeekCustomized(UUID tutorUserId, LocalDate weekStart) {
        TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
        return weekCustomizationRepository.existsByTutorProfile_TutorProfileIdAndWeekStart(
                tutorProfile.getTutorProfileId(), weekStart);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailabilityBlockResponse> getBlocksInRange(UUID tutorUserId, LocalDate from, LocalDate to) {
        TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);
        return findResponsesInRange(tutorProfile.getTutorProfileId(), from, to);
    }

    // Centraliza la lectura + truncado de bloques pasados, para no repetir el
    // mismo patrón (now, filtro por fecha/hora, effectiveStartTime) en los dos
    // lugares que devuelven el calendario: acá y al final de replaceWeekBlocks.
    private List<AvailabilityBlockResponse> findResponsesInRange(UUID tutorProfileId, LocalDate from, LocalDate to) {
        LocalDateTime now = LocalDateTime.now(AppTimeZone.ZONE);

        return availabilityBlockRepository
                .findInRange(tutorProfileId, from, to, now.toLocalDate(), now.toLocalTime())
                .stream()
                .map(block -> availabilityBlockMapper.toResponse(
                        block, PastTimeUtil.effectiveStartTime(block.getDate(), block.getStartTime(), now)))
                .toList();
    }
}