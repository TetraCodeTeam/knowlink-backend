package com.knowlink.api.payments.listeners;

import com.knowlink.api.bookings.events.SessionConfirmedEvent;
import com.knowlink.api.payments.services.FundsResolutionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceConfirmationListener {

    private final FundsResolutionService fundsResolutionService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSessionConfirmed(SessionConfirmedEvent event) {
        log.info("Session confirmed for booking {}, resolving funds",
                event.booking().getBookingId());

        try {
            fundsResolutionService.resolve(
                    event.booking().getBookingId(),
                    true,
                    true);
        } catch (Exception e) {
            log.error("Error resolving funds for booking {}: {}",
                    event.booking().getBookingId(), e.getMessage(), e);
        }
    }
}