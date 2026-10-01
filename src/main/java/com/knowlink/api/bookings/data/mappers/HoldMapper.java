// HoldMapper — ya no depende de nada, ni hace I/O
package com.knowlink.api.bookings.data.mappers;

import com.knowlink.api.bookings.controllers.responses.ActiveHoldResponse;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.users.data.models.User;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class HoldMapper {

    public ActiveHoldResponse toActiveHoldResponse(Hold hold, User tutor) {
        LocalDate date = hold.getTimeSlot().getDate();

        return new ActiveHoldResponse(
                hold.getHoldId(),
                tutor.getUserId(),
                tutor.getFullName(),
                hold.getTimeSlot().getTimeSlotId(),
                date.atTime(hold.getStartTime()).atZone(AppTimeZone.ZONE).toInstant(),
                date.atTime(hold.getEndTime()).atZone(AppTimeZone.ZONE).toInstant(),
                hold.getExpiresAt().atZone(AppTimeZone.ZONE).toInstant());
    }
}