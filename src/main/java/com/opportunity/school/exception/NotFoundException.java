package com.opportunity.school.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 404 with a friendly message.
 */
@Getter
public class NotFoundException extends RuntimeException {

    private final HttpStatus status = HttpStatus.NOT_FOUND;

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException entity(String name, Long id) {
        return new NotFoundException(name + " not found (id=" + id + ")");
    }
}
