package com.streampay.authentication.controller;


import com.streampay.authentication.dto.AuthResponse;
import com.streampay.authentication.dto.LoginRequestDto;
import com.streampay.authentication.dto.UserRegisterRequestDto;
import com.streampay.authentication.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {


    public final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

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
}
