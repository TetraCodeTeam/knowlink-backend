package com.knowlink.api.bookings.data.mappers;

import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.controllers.responses.BookingConfirmationResponse;
import com.knowlink.api.bookings.controllers.responses.BookingResponse;
import com.knowlink.api.bookings.controllers.responses.BookingHistoryItemResponse;
import com.knowlink.api.bookings.controllers.responses.BookingHistoryDetailResponse;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.users.data.models.User;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

@Component
public class BookingMapper {

        private record OtherParty(User user, boolean isTutor) {
        }

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
                                booking.getStudent().getFullName());
        }

        public BookingHistoryItemResponse toListItem(Booking booking, UUID viewerUserId,
                        Map<UUID, String> studentProfilePictureByUserId) {
                OtherParty otherParty = resolveOtherParty(booking, viewerUserId);
                String otherPartyProfilePictureUrl = otherParty.isTutor()
                                ? booking.getTutorSubject().getTutorProfile().getProfilePictureUrl()
                                : studentProfilePictureByUserId.get(booking.getStudent().getUserId());

                return new BookingHistoryItemResponse(
                                booking.getBookingId(),
                                otherParty.user().getFullName(),
                                otherPartyProfilePictureUrl,
                                booking.getTutorSubject().getSubject().getName(),
                                booking.getSessionDate(),
                                booking.getStartTime(),
                                booking.getEndTime(),
                                booking.getModality(),
                                booking.getBookingStatus(),
                                booking.getConfirmationTokenExpiration());
        }

        public BookingHistoryDetailResponse toDetail(Booking booking, UUID viewerUserId,
                        String otherPartyProfilePictureUrl) {
                OtherParty otherParty = resolveOtherParty(booking, viewerUserId);
                boolean isVirtual = booking.getModality() == Modality.VIRTUAL;

                return new BookingHistoryDetailResponse(
                                booking.getBookingId(),
                                otherParty.user().getFullName(),
                                otherPartyProfilePictureUrl,
                                booking.getTutorSubject().getSubject().getName(),
                                booking.getSessionDate(),
                                booking.getStartTime(),
                                booking.getEndTime(),
                                booking.getModality(),
                                booking.getBookingStatus(),
                                booking.getAmount(),
                                booking.getTopic(),
                                isVirtual ? booking.getVirtualSessionLink() : null,
                                isVirtual ? null : booking.getTutorSubject().getTutorProfile().getAddress(),
                                booking.getCreatedAt(),
                                booking.getConfirmationTokenExpiration());
        }

        private OtherParty resolveOtherParty(Booking booking, UUID viewerUserId) {
                boolean viewerIsStudent = booking.getStudent().getUserId().equals(viewerUserId);
                return viewerIsStudent
                                ? new OtherParty(booking.getTutor(), true)
                                : new OtherParty(booking.getStudent(), false);
        }

        public BookingConfirmationResponse toConfirmationResponse(Booking booking) {
                return new BookingConfirmationResponse(
                                booking.getBookingId(),
                                booking.getBookingStatus(),
                                booking.getConfirmedAt());
        }
}