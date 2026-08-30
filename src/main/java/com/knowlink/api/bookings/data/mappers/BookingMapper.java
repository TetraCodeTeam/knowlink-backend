package com.knowlink.api.bookings.data.mappers;

import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.controllers.responses.BookingResponse;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.users.data.models.User;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;

@Component
public class BookingMapper {

    public Booking toEntity(
            Hold hold, TutorSubject tutorSubject, User student, LocalTime startTime, LocalTime endTime,
            BigDecimal amount, CreateBookingRequest request) {

        return Booking.builder()
                .sessionDate(hold.getTimeSlot().getDate())
                .startTime(startTime)
                .endTime(endTime)
                .amount(amount)
                .modality(request.modality())
                .topic(request.topic())
                .bookingStatus(BookingStatus.BOOKED)
                .timeSlot(hold.getTimeSlot())
                .tutorSubject(tutorSubject)
                .student(student)
                .tutor(tutorSubject.getTutorProfile().getUser())
                .build();
    }

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getBookingId(),
                booking.getSessionDate(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getAmount(),
                booking.getModality(),
                booking.getTopic(),
                booking.getBookingStatus(),
                booking.getTutorSubject().getSubject().getName(),
                booking.getTutor().getFullName(),
                booking.getStudent().getFullName()
        );
    }
}