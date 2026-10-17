package com.eventcart.identity;

import com.eventcart.shared.DomainException;

public class EmailAlreadyUsedException extends DomainException {

    public EmailAlreadyUsedException(String email) {
        super("email already registered: " + email);
    }
}
