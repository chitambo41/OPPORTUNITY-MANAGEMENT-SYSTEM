package com.opportunity.school.exception;

import lombok.Getter;

/**
 * Business-rule violation -> HTTP 400 with a friendly message.
 */
@Getter
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
