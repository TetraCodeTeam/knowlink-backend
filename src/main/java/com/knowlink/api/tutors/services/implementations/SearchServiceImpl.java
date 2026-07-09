package com.knowlink.api.tutors.services.implementations;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.knowlink.api.tutors.data.dto.responses.TutorSearchResponse;
import com.knowlink.api.tutors.data.models.MateriaTutor;
import com.knowlink.api.tutors.mappers.TutorSearchMapper;
import com.knowlink.api.tutors.repositories.MateriaTutorRepository;
import com.knowlink.api.tutors.services.interfaces.SearchService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl { // implements SearchService 
/* 
        private final MateriaTutorRepository materiaTutorRepository;

        public List<TutorSearchResponse> search(String query) {

                List<MateriaTutor> resultados = materiaTutorRepository.search(query);

                Map<UUID, List<MateriaTutor>> agrupados = resultados.stream()
                                .collect(Collectors.groupingBy(
                                                mt -> mt.getPerfilTutor().getUser().getUserId()));

                return agrupados.values()
                                .stream()
                                .map(TutorSearchMapper::from)
                                .toList();
        }

        private TutorSearchResponse toTutorResponse(List<MateriaTutor> materiasTutor) {

                MateriaTutor primero = materiasTutor.get(0);

                return new TutorSearchResponse(
                                primero.getPerfilTutor().getUser().getUserId(),
                                primero.getPerfilTutor().getUser().getFullName(),
                                primero.getPerfilTutor().getFotoPerfil(),
                                primero.getPerfilTutor().getCalificacionPromedio(),
                                primero.getPerfilTutor().getCantidadResenas(),
                                materiasTutor.stream()
                                                .map(mt -> mt.getMateria().getNombre())
                                                .distinct()
                                                .toList());
        }
                                                */
}