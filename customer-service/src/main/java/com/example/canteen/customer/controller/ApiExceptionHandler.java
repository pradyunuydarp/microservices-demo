package com.example.canteen.customer.controller;

import com.example.canteen.customer.service.CustomerNotFoundException;
import com.example.canteen.customer.service.EmailAlreadyRegisteredException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/** Turns exceptions into RFC 9457 problem details (application/problem+json). */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handleNotFound(CustomerNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Customer not found");
        return problem;
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleDuplicateEmail(EmailAlreadyRegisteredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Email already registered");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = ex.getBody();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
