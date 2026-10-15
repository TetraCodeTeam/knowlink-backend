package com.knowlink.api.tutors.controllers.implementations;

import com.knowlink.api.tutors.controllers.interfaces.ITutorRatingHistoryController;
import com.knowlink.api.tutors.controllers.responses.TutorRatingHistoryResponse;
import com.knowlink.api.tutors.services.interfaces.ITutorRatingHistoryService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TutorRatingHistoryControllerImpl implements ITutorRatingHistoryController {

    private final ITutorRatingHistoryService tutorRatingHistoryService;

    @Override
    public TutorRatingHistoryResponse getRatingHistory(UUID tutorId, UUID subjectId, int page, int size) {
        return tutorRatingHistoryService.getRatingHistory(tutorId, subjectId, page, size);
    }
}