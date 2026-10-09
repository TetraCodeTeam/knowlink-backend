package com.knowlink.api.tutors.services.interfaces;

import com.knowlink.api.tutors.data.models.Career;

import java.util.List;

public interface ICareerService {
    List<Career> findAll();
}