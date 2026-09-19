package com.a501.service.domain.user.dto;

public record UserLoginResult(UserSnapshot user, boolean newUser, boolean withdrawn) {
    public static UserLoginResult accepted(UserSnapshot user, boolean newUser) {
        return new UserLoginResult(user, newUser, false);
    }

    public static UserLoginResult rejectedWithdrawnUser() {
        return new UserLoginResult(null, false, true);
    }
}
