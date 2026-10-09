package com.knowlink.api.ratings.services.interfaces;

import com.knowlink.api.ratings.data.models.Rating;

import java.util.List;

public interface IRatingReputationService {
    void refreshTutorAverages(List<Rating> ratings);
}