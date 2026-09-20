package com.a501.escrow.common.security;

public record ApiRequirements(
        boolean idempotencyKeyRequired,
        boolean userKeyRequired,
        boolean writerRequired,
        boolean operatorOnly
) {
    public static ApiRequirements read() {
        return new ApiRequirements(false, false, false, false);
    }

    public static ApiRequirements write(boolean userKeyRequired) {
        return new ApiRequirements(true, userKeyRequired, true, false);
    }

    public static ApiRequirements operatorRead() {
        return new ApiRequirements(false, false, false, true);
    }

}
