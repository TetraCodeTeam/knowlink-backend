package com.knowlink.api.payments.controllers.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.payments.controllers.interfaces.IFundsController;
import com.knowlink.api.payments.controllers.responses.FundsTransferResponseDTO;
import com.knowlink.api.payments.services.FundsResolutionService;
import com.knowlink.api.security.models.UserPrincipal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FundsControllerImpl implements IFundsController {

    private final FundsResolutionService fundsResolutionService;

    @Override
    public ResponseEntity<FundsTransferResponseDTO> getFundsStatus(
            UserPrincipal principal, UUID bookingId) {
        return fundsResolutionService.getFundsStatus(bookingId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FUNDS_TRANSFER_NOT_FOUND",
                        "No existe una transferencia de fondos asociada a esta reserva.",
                        "No funds transfer found for booking: " + bookingId));
    }
}