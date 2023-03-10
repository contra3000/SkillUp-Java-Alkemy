package com.alkemy.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUserDto {

    @Email(message = "{email.pattern}")
    @NotEmpty(message = "{email.notnull}")
    private String email;

    @NotEmpty(message = "{password.notnull")
    private String password;

}
