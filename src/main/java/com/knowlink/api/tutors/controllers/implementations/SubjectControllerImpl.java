package com.knowlink.api.tutors.controllers.implementations;

import com.knowlink.api.tutors.controllers.interfaces.ISubjectController;
import com.knowlink.api.tutors.controllers.responses.SubjectResponse;
import com.knowlink.api.tutors.services.interfaces.ISubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SubjectControllerImpl implements ISubjectController {

    private final ISubjectService subjectService;

    @Override
    public List<SubjectResponse> getBasicSubjects() {
        return subjectService.findBasicSubjects().stream()
                .map(s -> new SubjectResponse(s.getSubjectId(), s.getName(), s.isBasic()))
                .toList();
    }

    @Override
    public List<SubjectResponse> getSubjectsByCareer(UUID careerId) {
        return subjectService.findByCareerId(careerId).stream()
                .map(s -> new SubjectResponse(s.getSubjectId(), s.getName(), s.isBasic()))
                .toList();
    }
}