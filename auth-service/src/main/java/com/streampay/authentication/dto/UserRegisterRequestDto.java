package com.streampay.authentication.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserRegisterRequestDto(

        @NotBlank
        @Email
        String email,

        @NotBlank
        String password,

        @NotBlank
        String role

) {
}
