package com.warehouse.exeption_handling;

import com.warehouse.exeption_handling.exeptions.ThirdTestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ThirdTestException.class)
    public ResponseEntity<Response> handleThirdTestException(ThirdTestException e) {
        Response response = new Response(e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationResponse> handleValidationException(MethodArgumentNotValidException e) {
        List<String> errors = new ArrayList<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.add(error.getField() + " | " + error.getDefaultMessage());
        }
        ValidationResponse response = new ValidationResponse(errors);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // <<< НОВОЕ: неавторизован — 401, чтобы фронт сделал /auth/refresh >>>
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Response> handleAccessDenied(AccessDeniedException e) {
        // Тело можно не возвращать — фронту достаточно кода 401.
        // Но пусть будет компактный ответ:
        return new ResponseEntity<>(new Response(e.getMessage()), HttpStatus.UNAUTHORIZED);
    }

    // (Опционально) Любые необработанные — 500 с кратким сообщением
    // @ExceptionHandler(Exception.class)
    // public ResponseEntity<Response> handleAny(Exception e) {
    //     return new ResponseEntity<>(new Response("Внутренняя ошибка сервера"), HttpStatus.INTERNAL_SERVER_ERROR);
    // }
}
