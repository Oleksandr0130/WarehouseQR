package com.warehouse.exeption_handling;


import com.warehouse.exeption_handling.exeptions.ThirdTestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.ArrayList;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ThirdTestException.class)
    public ResponseEntity<Response> handleThirdTestException(ThirdTestException e) {
        Response response = new Response(e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationResponse> handleValidationException(MethodArgumentNotValidException e) {
        // создаем список ошибок для накопления сообщений
        List<String> errors = new ArrayList<>();

        // перебираем все ошибки
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            // добовляем сообщение об ошибки для текущего поля
           errors.add(error.getField() + " | " + error.getDefaultMessage());
        }

        // Сщздаем обьект Response с накопленным сообщением
        ValidationResponse response = new ValidationResponse(errors);

        // возвращаем ResponseEntity с обьектом Response и статусом 400

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

//    @ExceptionHandler(MethodArgumentNotValidException.class)
//    public ResponseEntity<Response> handleValidationException(MethodArgumentNotValidException e) {
//        // создам обьект StringBuilder для накопления сообщений
//        StringBuilder errorMessage = new StringBuilder();
//
//        // перебираем все ошибки
//        for (FieldError error : e.getBindingResult().getFieldErrors()) {
//            // добовляем сообщение об ошибки для текущего поля
//            errorMessage.append(error.getDefaultMessage()).append("; ");
//        }
//
//        // Сщздаем обьект Response с накопленным сообщением
//        Response response = new Response(errorMessage.toString());
//
//        // возвращаем ResponseEntity с обьектом Response и статусом 400
//
//        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
//    }


}
