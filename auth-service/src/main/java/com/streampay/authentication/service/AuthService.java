package com.streampay.authentication.service;

import com.streampay.authentication.dto.*;
import com.streampay.authentication.entities.RefreshToken;
import com.streampay.authentication.entities.User;
import com.streampay.authentication.exception.*;
import com.streampay.authentication.repository.RefreshTokenRepository;
import com.streampay.authentication.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.core.Local;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final RefreshTokenRepository refreshTokenRepository;

    private final long refreshTokenExpiration;

    private final TokenHashService tokenHashService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, RefreshTokenRepository refreshTokenRepository, @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration, TokenHashService tokenHashService)
    {
        this.userRepository = userRepository;
        this.passwordEncoder=passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.tokenHashService = tokenHashService;
    }

    public void register(UserRegisterRequestDto userRegisterRequestDto)
    {
        if (userRepository.existsByEmail(userRegisterRequestDto.email())) {
            throw new UserAlreadyExistsException("Email already registered");
        }



        User user= User.builder().email(userRegisterRequestDto.email()).
                                  role("ROLE_"+userRegisterRequestDto.role()).
                                  password(passwordEncoder.encode(userRegisterRequestDto.password())).
                                  merchantId("MERCHANT".equals(userRegisterRequestDto.role()) ? generateMerchantId() : null).
                                  createdAt(LocalDateTime.now()).build();


        userRepository.save(user);
    }

    private String generateMerchantId() {
        return "MER-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
    }


    public AuthResponse login(LoginRequestDto loginRequestDto){
        // details from client
        String email=loginRequestDto.email();
        String password=loginRequestDto.password();

        // details of user from DB
        User user=userRepository.findByEmail(email).orElseThrow(()->new UserNotFoundException("User not found"));


        if(!passwordEncoder.matches(password, user.getPassword())){
             throw new InvalidCredentialsException("Incorrect Password");
        }

        // generate JWT tokens
        String accessToken=jwtService.generateAccessToken(user.getEmail(),user.getRole(),user.getMerchantId(),user.getId().toString());
        String refreshToken= jwtService.generateRefreshToken(user.getEmail());

        // store refresh token in DB
        RefreshToken refreshTokenEntity= RefreshToken.builder().
                                            tokenHash(tokenHashService.hash(refreshToken)).
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
            throw new InvalidTokenException("Invalid or expired refresh token");
        }


        String tokenType=claims.get("tokenType",String.class);
        if(!"refresh".equals(tokenType))
        {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }
        String tokenHash=tokenHashService.hash(refreshTokenRequest.refreshToken());
        RefreshToken refreshToken=refreshTokenRepository.findByTokenHash(tokenHash)
                                                         .orElseThrow(()-> new InvalidTokenException("Refresh token doesnt exist"));

        if(refreshToken.isRevoked()){
            throw new TokenRevokedException("Token revoked, Login again");
        }
        if(!refreshToken.getExpiresAt().isAfter(LocalDateTime.now())){
           //  jwtService.generateRefreshToken(claims.getSubject(),claims.get("role").toString());
            throw new TokenExpiredException("Token Expired,Login again");
        }

        // old token revoke
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // generate new tokens
        User user=refreshToken.getUser();
        String newAccessToken=jwtService.generateAccessToken(claims.getSubject(), user.getRole(), user.getMerchantId(),user.getId().toString());
        String newRefreshToken= jwtService.generateRefreshToken(claims.getSubject());

        RefreshToken newRefreshTokenEntity=RefreshToken.builder().
                                                        tokenHash(tokenHashService.hash(newRefreshToken)).
                                                         user(user).
                                                         revoked(false).
                                                          createdAt(LocalDateTime.now()).
                                                           expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration/1000)).build();

        //store new refesh token in DB
        refreshTokenRepository.save(newRefreshTokenEntity);

        return new AuthResponse(newAccessToken,newRefreshToken,"Bearer");
    }


    public LogoutResponse logout(String rawRefreshToken) {

        // Validate JWT signature + expiry
        try {
            jwtService.extractClaims(rawRefreshToken);
        } catch (Exception e) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        String tokenHash = tokenHashService.hash(rawRefreshToken);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new InvalidTokenException("Refresh token does not exist")
                );

        if(refreshToken.isRevoked())
        {
            return new LogoutResponse("Already Logged out",LocalDateTime.now());
        }


        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        return new LogoutResponse("Logged out successfully", LocalDateTime.now());
    }


}
