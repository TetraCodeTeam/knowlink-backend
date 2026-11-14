package com.knowlink.api.tutors.availability.validations;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
import com.knowlink.api.tutors.availability.data.models.AvailabilityBlock;

public interface IAvailabilityBlockValidationService {
    void validateBlocks(List<AvailabilityBlockRequest> blocks);
    void validateWeekRequest(LocalDate weekStart, LocalDate weekEnd, List<AvailabilityBlockRequest> blocks);

    /**
     * Verifica que cada bloque bloqueado por reservas vuelva en la solicitud sin cambios y devuelve
     * los bloques restantes, que son los únicos que se pueden reemplazar.
     *
     * @throws com.knowlink.api.exceptions.custom_exceptions.ValidationException si la solicitud
     *         modifica o quita un bloque bloqueado.
     */
    List<AvailabilityBlockRequest> excludeLockedBlocks(
            List<AvailabilityBlock> lockedBlocks, List<AvailabilityBlockRequest> requested, LocalDateTime now);
}
