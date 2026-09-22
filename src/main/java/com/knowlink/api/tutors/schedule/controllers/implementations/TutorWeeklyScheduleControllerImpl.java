package com.knowlink.api.tutors.schedule.controllers.implementations;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.schedule.controllers.interfaces.ITutorWeeklyScheduleController;
import com.knowlink.api.tutors.schedule.controllers.responses.WeeklyScheduleResponse;
import com.knowlink.api.tutors.schedule.services.interfaces.ITutorWeeklyScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class TutorWeeklyScheduleControllerImpl implements ITutorWeeklyScheduleController {

    private final ITutorWeeklyScheduleService weeklyScheduleService;

    @Override
    public WeeklyScheduleResponse getWeeklySchedule(UserPrincipal principal, LocalDate from, LocalDate to) {
        return weeklyScheduleService.getWeeklySchedule(
                principal.getUser().getUserId(), from, to);
    }
}
