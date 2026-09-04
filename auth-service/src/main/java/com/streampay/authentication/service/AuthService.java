package com.streampay.authentication.service;

import com.streampay.authentication.dto.AuthResponse;
import com.streampay.authentication.dto.LoginRequestDto;
import com.streampay.authentication.dto.UserRegisterRequestDto;
import com.streampay.authentication.entities.User;
import com.streampay.authentication.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService)
    {
        this.userRepository = userRepository;
        this.passwordEncoder=passwordEncoder;
        this.jwtService = jwtService;
    }

    public void register(UserRegisterRequestDto userRegisterRequestDto)
    {
        if (userRepository.existsByEmail(userRegisterRequestDto.email())) {
            throw new RuntimeException("Email already registered");
        }

        User user= User.builder().email(userRegisterRequestDto.email()).
                                  role("ROLE_"+userRegisterRequestDto.role()).
                                  password(passwordEncoder.encode(userRegisterRequestDto.password())).
                                  createdAt(LocalDateTime.now()).build();

        userRepository.save(user);
    }


    public AuthResponse login(LoginRequestDto loginRequestDto){
        // details from client
        String email=loginRequestDto.email();
        String password=loginRequestDto.password();

        // details of user from DB
        User user=userRepository.findByEmail(email).orElseThrow(()->new RuntimeException("User does not exist"));


        if(!passwordEncoder.matches(password, user.getPassword())){
             throw new RuntimeException("Password is incorrect");
        }

        // generate JWT tokens
        String accessToken=jwtService.generateAccessToken(user.getEmail(),user.getRole());
        String refreshToken= jwtService.generateRefreshToken(user.getEmail(), user.getRole());


        // return the response to the user
        return new AuthResponse(accessToken,refreshToken,"Bearer");

    }


}
