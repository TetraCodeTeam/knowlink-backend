package com.knowlink.api.ratings.data.mappers;

import com.knowlink.api.ratings.controllers.requests.CreateRatingRequest;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.users.data.models.User;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RatingMapper {

	public Rating toEntity(Booking booking, User rater, User rated, CreateRatingRequest request,
			LocalDateTime ratingDate, boolean visible) {
		return Rating.builder()
				.booking(booking)
				.raterUser(rater)
				.ratedUser(rated)
				.score(request.score())
				.comment(request.comment())
				.ratingDate(ratingDate)
				.visible(visible)
				.build();
	}
}
