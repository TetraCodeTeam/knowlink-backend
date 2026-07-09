package com.knowlink.api.tutors.services.interfaces;

import com.knowlink.api.tutors.data.dto.responses.TutorSearchResponse;

import java.util.List;

public interface SearchService {

    List<TutorSearchResponse> search(String query);

}