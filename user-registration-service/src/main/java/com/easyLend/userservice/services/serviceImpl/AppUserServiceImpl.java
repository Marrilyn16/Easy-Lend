package com.easyLend.userservice.services.serviceImpl;

import com.easyLend.userservice.domain.constant.UserType;
import com.easyLend.userservice.domain.entity.AppUser;
import com.easyLend.userservice.domain.entity.JwtToken;
import com.easyLend.userservice.domain.repository.AppUserRepository;
import com.easyLend.userservice.domain.repository.JwtTokenRepository;
import com.easyLend.userservice.event.RegisterEvent;
import com.easyLend.userservice.exceptions.PasswordNotFoundException;
import com.easyLend.userservice.exceptions.UserAlreadyExistExceptions;
import com.easyLend.userservice.request.LoginRequest;
import com.easyLend.userservice.request.RegisterRequest;
import com.easyLend.userservice.response.LoginResponse;
import com.easyLend.userservice.response.RegisterResponse;
import com.easyLend.userservice.security.JwtService;
import com.easyLend.userservice.services.AppUserService;
import com.easyLend.userservice.utils.EmailUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AppUserServiceImpl implements AppUserService {
    private final AppUserRepository appUserRepository;
    private final ApplicationEventPublisher publisher;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;
    private final JwtService jwtService;
    private final JwtTokenRepository jwtTokenRepository;
    @Value("${application.security.jwt.expiration}")
    private long expirationTime;

    public AppUser confirmUserExists(String email){
        return appUserRepository.findAppUserByEmail(email).orElseThrow(()-> new UserAlreadyExistExceptions("USER NOT FOUND"));
    }
    private void confirmUser(String email){
        Boolean appUser = appUserRepository.existsAppUserByEmail(email);
        if (appUser){
            throw new UserAlreadyExistExceptions("USER ALREADY EXIST");
        }
    }
    @Override
    public RegisterResponse registerUser(RegisterRequest request, UserType userType, HttpServletRequest httpServletRequest) {
             confirmUser(request.getEmail());
            AppUser appUser = appUserRepository.save(saveUserDTO(request));
            publisher.publishEvent(new RegisterEvent(appUser, EmailUtils.applicationUrl(httpServletRequest)));
            return modelMapper.map(appUser,RegisterResponse.class);


    }

    @Override
    public LoginResponse loginAuth(LoginRequest loginRequest) {
        AppUser user = confirmUserExists(loginRequest.getEmail());
        if(user.getRegistrationStatus()){
            if(!passwordEncoder.matches(loginRequest.getPassword(),user.getPassword())){
                throw new PasswordNotFoundException("Password does not match");

            }
            JwtToken token = jwtTokenRepository.findByUser(user);
            if (token != null) {
                System.out.println(token.getAccessToken());
                jwtTokenRepository.delete(token);
            }
            String jwt = jwtService.generateToken(user);
            String refresh = jwtService.generateRefreshToken(user);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    user.getEmail(),user.getPassword());

            JwtToken jwtToken = JwtToken.builder()
                    .accessToken(jwt)
                    .refreshToken(refresh)
                    .user(user)
                    .generatedAt(new Date(System.currentTimeMillis()))
                    .expiresAt(new Date(System.currentTimeMillis() + expirationTime))
                    .build();

            JwtToken savedToken = jwtTokenRepository.save(jwtToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return LoginResponse.builder()
                    .activate(savedToken.getUser().getRegistrationStatus())
                    .accessToken(savedToken.getAccessToken())
                    .refreshToken(savedToken.getRefreshToken())
                    .username(savedToken.getUser().getUsername())
                    .email(savedToken.getUser().getEmail())
                    .build();
        }


        return null;
    }

    private AppUser saveUserDTO(RegisterRequest request){
        return AppUser.builder()
                .userType(request.getUserType())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .createdAt(LocalDateTime.now())
                .registrationStatus(true)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
    }


}
