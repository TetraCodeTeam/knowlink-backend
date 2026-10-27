package com.knowlink.api.tutors.statistics.controllers.implementations;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.statistics.controllers.interfaces.ITutorStatisticsController;
import com.knowlink.api.tutors.statistics.controllers.responses.TutorStatisticsResponse;
import com.knowlink.api.tutors.statistics.data.enums.StatisticsPeriodType;
import com.knowlink.api.tutors.statistics.services.interfaces.ITutorStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class TutorStatisticsControllerImpl implements ITutorStatisticsController {

    private final ITutorStatisticsService tutorStatisticsService;

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    public TutorStatisticsResponse getStatistics(
            UserPrincipal principal, StatisticsPeriodType period, LocalDate from, LocalDate to) {
        return tutorStatisticsService.getStatistics(principal.getUser().getUserId(), period, from, to);
    }
}
