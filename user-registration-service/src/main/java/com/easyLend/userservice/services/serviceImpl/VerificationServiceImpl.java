package com.easyLend.userservice.services.serviceImpl;

import com.easyLend.userservice.domain.entity.AppUser;
import com.easyLend.userservice.domain.entity.VerificationEmail;
import com.easyLend.userservice.domain.repository.AppUserRepository;
import com.easyLend.userservice.domain.repository.VerificationEmailRepository;
import com.easyLend.userservice.event.RegisterEvent;
import com.easyLend.userservice.exceptions.TokenNotFoundException;
import com.easyLend.userservice.services.VerificationEmailService;
import com.easyLend.userservice.utils.EmailUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationEmailService {
    private final AppUserServiceImpl appUserService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher publisher;
    private final VerificationEmailRepository emailRepository;

    @Override
    public String findOtp() {
        return null;
    }

    @Override
    public void saveToken(VerificationEmail confirmationToken) {

        emailRepository.save(confirmationToken);

    }


    @Override
    public String forgotPassword(String token) {
        VerificationEmail confirmUserExists = emailRepository.findByToken(token);
        if(confirmUserExists.getExpiresAt().before(new Date())) {
            emailRepository.delete(confirmUserExists);
            throw new TokenNotFoundException("TOKEN EXPIRED");
        }
        return confirmUserExists.getUser().getEmail();

    }

    @Override
    public String verifyUser(String token, HttpServletRequest request) {
        VerificationEmail verificationEmail = emailRepository.findByToken(token);
        AppUser appUser = verificationEmail.getUser();
        if (appUser.getRegistrationStatus()){
            return "USER ALREADY VERIFIED";
        }
        if(verificationEmail.getExpiresAt().before(new Date())){
            emailRepository.delete(verificationEmail);
            return "Verification Link closed" +
                    "" +
                    "Please click the link to get a new link" +
                    EmailUtils.applicationUrl(request)+"api/v1/auth/new-verification?email="+appUser.getEmail();

        }
        appUser.setRegistrationStatus(true);
        appUserRepository.save(appUser);

        return "USER VERIFIED";

    }

    @Override
    public String sendNewVerificationLink(String email, HttpServletRequest request) {
        AppUser appUser = appUserService.confirmUserExists(email);
        if(appUser.getRegistrationStatus()){
            return "USER ALREADY VERIFIED PLEASE LOG IN";
        }
        VerificationEmail confirmUser = emailRepository.findByUser(appUser);
        if(confirmUser!=null){
            emailRepository.delete(confirmUser);
        }
        publisher.publishEvent(new RegisterEvent(appUser, EmailUtils.applicationUrl(request)));
        return "please check your email for verification link";
    }
}
