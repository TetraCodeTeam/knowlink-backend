package com.knowlink.api.tutors.data.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.users.data.models.User;

@Entity
@Table(name = "rating")
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rating_id", updatable = false, nullable = false)
    private UUID ratingId;

    @ManyToOne(fetch = FetchType.LAZY) // agregar para sostener ambosCalificaron() 
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rated_user_id", nullable = false) 
    private User ratedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rater_user_id", nullable = false) 
    private User raterUser;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "rating_date", nullable = false)
    private LocalDateTime ratingDate;

    @Column(name = "visible", nullable = false)
    private boolean visible;

    public void makeVisible() { this.visible = true; }
}