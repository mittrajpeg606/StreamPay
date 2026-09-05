package com.streampay.authentication.service;

import com.streampay.authentication.dto.AuthResponse;
import com.streampay.authentication.dto.LoginRequestDto;
import com.streampay.authentication.dto.RefreshTokenRequest;
import com.streampay.authentication.dto.UserRegisterRequestDto;
import com.streampay.authentication.entities.RefreshToken;
import com.streampay.authentication.entities.User;
import com.streampay.authentication.repository.RefreshTokenRepository;
import com.streampay.authentication.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final RefreshTokenRepository refreshTokenRepository;

    private final long refreshTokenExpiration;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, RefreshTokenRepository refreshTokenRepository, @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration)
    {
        this.userRepository = userRepository;
        this.passwordEncoder=passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
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
        String refreshToken= jwtService.generateRefreshToken(user.getEmail());

        // store refresh token in DB
        RefreshToken refreshTokenEntity= RefreshToken.builder().
                                            token(refreshToken).
                                            revoked(false).
                                            user(user).
                                            createdAt(LocalDateTime.now()).
                                            expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration/1000)).build();


        refreshTokenRepository.save(refreshTokenEntity);

        // return the response to the user
        return new AuthResponse(accessToken,refreshToken,"Bearer");

    }


    public AuthResponse refreshToken(RefreshTokenRequest refreshTokenRequest)
    {

        Claims claims;

        try {
            claims = jwtService.extractClaims(refreshTokenRequest.refreshToken());
        } catch (Exception e) {
            throw new RuntimeException("Invalid or expired refresh token");
        }


        String tokenType=claims.get("tokenType",String.class);
        if(!"refresh".equals(tokenType))
        {
            throw new RuntimeException("Invalid Refresh token");
        }

        RefreshToken refreshToken=refreshTokenRepository.findByToken(refreshTokenRequest.refreshToken())
                                                         .orElseThrow(()-> new RuntimeException("Refresh token doesnt exist"));

        if(refreshToken.isRevoked()){
            throw new RuntimeException("Token revoked, Login again");
        }
        if(!refreshToken.getExpiresAt().isAfter(LocalDateTime.now())){
           //  jwtService.generateRefreshToken(claims.getSubject(),claims.get("role").toString());
            throw new RuntimeException("Token Expired,Login again");
        }

        // old token revoke
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // generate new tokens
        User user=refreshToken.getUser();
        String newAccessToken=jwtService.generateAccessToken(claims.getSubject(), user.getRole());
        String newRefreshToken= jwtService.generateRefreshToken(claims.getSubject());

        RefreshToken newRefreshTokenEntity=RefreshToken.builder().
                                                        token(newRefreshToken).
                                                         user(user).
                                                         revoked(false).
                                                          createdAt(LocalDateTime.now()).
                                                           expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration/1000)).build();

        //store new refesh token in DB
        refreshTokenRepository.save(newRefreshTokenEntity);

        return new AuthResponse(newAccessToken,newRefreshToken,"Bearer");
    }


}
