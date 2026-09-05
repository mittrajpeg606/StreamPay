package com.streampay.authentication.controller;


import com.streampay.authentication.dto.AuthResponse;
import com.streampay.authentication.dto.LoginRequestDto;
import com.streampay.authentication.dto.RefreshTokenRequest;
import com.streampay.authentication.dto.UserRegisterRequestDto;
import com.streampay.authentication.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {


    private final AuthService authService;



    @PostMapping("/register")
    public String registerUser(@Valid @RequestBody UserRegisterRequestDto userRegisterRequestDto)
    {
        authService.register(userRegisterRequestDto);
        return "User Registered Successfully";
    }


    @PostMapping("/login")
    public AuthResponse registerUser(@Valid @RequestBody LoginRequestDto loginRequestDto)
    {
        return authService.login(loginRequestDto);
    }

    @PostMapping("/refresh")
    public AuthResponse refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        return authService.refreshToken(request);
    }


    @GetMapping("/customer")
    public String customer() {
        return "Customer endpoint accessed";
    }

    @GetMapping("/merchant")
    public String merchant() {
        return "Merchant endpoint accessed";
    }

    @GetMapping("/admin")
    public String admin() {
        return "Admin endpoint accessed";
    }



    @GetMapping("/authenticatedUser")
    public Map<String, Object> getCurrentUser(Authentication authentication) {

        return Map.of("email", authentication.getName(),"role", authentication.getAuthorities().iterator().next().getAuthority());
    }
}
