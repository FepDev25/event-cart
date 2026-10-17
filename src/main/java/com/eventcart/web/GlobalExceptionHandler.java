package com.eventcart.web;

import com.eventcart.identity.EmailAlreadyUsedException;
import com.eventcart.identity.InvalidCredentialsException;
import com.eventcart.launch.InsufficientStockException;
import com.eventcart.launch.LaunchNotOpenException;
import com.eventcart.launch.OutsideSaleWindowException;
import com.eventcart.reservation.PurchaseLimitExceededException;
import com.eventcart.shared.DomainException;
import com.eventcart.shared.ForbiddenException;
import com.eventcart.shared.IdempotencyConflictException;
import com.eventcart.shared.InvalidStateTransitionException;
import com.eventcart.shared.MissingIdempotencyKeyException;
import com.eventcart.shared.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Traduce excepciones a respuestas RFC 9457.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        return problem(statusFor(ex), typeFor(ex), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .reduce((left, right) -> left + "; " + right)
                .orElse("invalid request");
        return problem(HttpStatus.BAD_REQUEST, "validation-failed", detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation-failed", ex.getMessage());
    }

    private static HttpStatus statusFor(DomainException ex) {
        if (ex instanceof InvalidCredentialsException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof MissingIdempotencyKeyException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof ForbiddenException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof InsufficientStockException
                || ex instanceof PurchaseLimitExceededException
                || ex instanceof LaunchNotOpenException
                || ex instanceof OutsideSaleWindowException
                || ex instanceof InvalidStateTransitionException
                || ex instanceof IdempotencyConflictException
                || ex instanceof EmailAlreadyUsedException) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    private static String typeFor(DomainException ex) {
        if (ex instanceof InvalidCredentialsException) {
            return "invalid-credentials";
        }
        if (ex instanceof MissingIdempotencyKeyException) {
            return "missing-idempotency-key";
        }
        if (ex instanceof ForbiddenException) {
            return "forbidden";
        }
        if (ex instanceof NotFoundException) {
            return "not-found";
        }
        if (ex instanceof IdempotencyConflictException) {
            return "idempotency-conflict";
        }
        if (ex instanceof InsufficientStockException) {
            return "stock-insufficient";
        }
        if (ex instanceof PurchaseLimitExceededException) {
            return "purchase-limit-exceeded";
        }
        if (ex instanceof LaunchNotOpenException) {
            return "launch-not-open";
        }
        if (ex instanceof OutsideSaleWindowException) {
            return "outside-sale-window";
        }
        if (ex instanceof InvalidStateTransitionException) {
            return "invalid-state-transition";
        }
        if (ex instanceof EmailAlreadyUsedException) {
            return "email-already-used";
        }
        return "domain-error";
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("urn:eventcart:error:" + type));
        return problem;
    }
}
