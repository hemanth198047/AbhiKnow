package com.abhiknow.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail badRequest(Exception e) {
        ProblemDetail p = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        p.setTitle("Invalid request");
        p.setDetail(e instanceof MethodArgumentTypeMismatchException ? "Invalid value for parameter" : e.getMessage());
        return p;
    }
}
