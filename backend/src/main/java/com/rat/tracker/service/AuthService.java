package com.rat.tracker.service;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.rat.tracker.dto.LoginRequest;
import com.rat.tracker.dto.LoginResponse;
import com.rat.tracker.dto.RefreshTokenRequest;
import com.rat.tracker.entity.AppUser;
import com.rat.tracker.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final SessionService sessionService;

    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        AppUser appUser = appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        return sessionService.startSession(appUser);
    }

    public LoginResponse refresh(RefreshTokenRequest request) {
        return sessionService.refresh(request.getRefreshToken());
    }

    public void logout(RefreshTokenRequest request) {
        sessionService.logout(request.getRefreshToken());
    }
}
