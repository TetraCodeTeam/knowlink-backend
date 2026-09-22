package com.knowlink.api.timeslot.data.mappers;

import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class TimeSlotGenerator {

    public List<TimeSlot> generate(AvailabilityBlock template, LocalDate from, LocalDate to) {
        List<TimeSlot> slots = new ArrayList<>();
        long weeks = template.isRepeatWeekly()
                ? ChronoUnit.WEEKS.between(from, to) + 1
                : 1;

        for (long i = 0; i < weeks; i++) {
            slots.add(TimeSlot.builder()
                    .date(from.plusWeeks(i))
                    .startTime(template.getStartTime())
                    .endTime(template.getEndTime())
                    .status(SlotStatus.AVAILABLE)
                    .tutorProfileId(template.getTutorProfile().getTutorProfileId())
                    .assignedBlock(template)
                    .build());
        }
        return slots;
    }
}
