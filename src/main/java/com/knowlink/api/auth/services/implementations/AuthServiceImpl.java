package com.knowlink.api.auth.services.implementations;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.UserRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import com.knowlink.api.auth.services.interfaces.IAuthService;
import com.knowlink.api.users.services.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final IUserService userService;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );
        return userService.verifyUser(request);
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User", "email", request.email());
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new PasswordsDoNotMatchException();
        }
        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ALUMNO)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);
    }
}