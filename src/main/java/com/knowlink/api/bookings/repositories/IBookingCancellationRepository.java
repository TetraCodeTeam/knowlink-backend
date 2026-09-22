package com.knowlink.api.bookings.repositories;

import com.knowlink.api.bookings.data.models.BookingCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IBookingCancellationRepository extends JpaRepository<BookingCancellation, UUID> {
}
