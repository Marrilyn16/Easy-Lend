package com.easyLend.userservice.request;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}
