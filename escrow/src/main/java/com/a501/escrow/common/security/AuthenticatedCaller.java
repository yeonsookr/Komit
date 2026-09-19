package com.a501.escrow.common.security;

public sealed interface AuthenticatedCaller permits AuthenticatedCaller.Client, AuthenticatedCaller.Operator {

    record Client(long serviceClientId, String name) implements AuthenticatedCaller {
    }

    record Operator() implements AuthenticatedCaller {
    }
}
