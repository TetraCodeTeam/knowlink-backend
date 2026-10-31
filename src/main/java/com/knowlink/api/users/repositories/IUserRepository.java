package com.knowlink.api.users.repositories;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IUserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM User u WHERE u.email = :email AND u.accountStatus != 'DELETED'")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.accountStatus != 'DELETED'")
    Optional<User> findByEmail(@Param("email") String email);

    Optional<User> findByUserIdAndAccountStatusNot(UUID userId, AccountStatus accountStatus);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM User u WHERE u.dni = :dni AND u.accountStatus != 'DELETED'")
    boolean existsByDni(@Param("dni") String dni);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM User u WHERE u.dni = :dni AND u.userId <> :userId AND u.accountStatus != 'DELETED'")
    boolean existsByDniAndUserIdNot(@Param("dni") String dni, @Param("userId") UUID userId);

    List<User> findAllByRoleAndAccountStatus(Role role, AccountStatus accountStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);
}