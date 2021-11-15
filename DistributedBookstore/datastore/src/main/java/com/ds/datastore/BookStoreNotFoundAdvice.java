package com.ds.datastore;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class BookStoreNotFoundAdvice {

    @ResponseBody
    @ExceptionHandler(BookStoreNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    protected String bookStoreNotFoundHandler(BookStoreNotFoundException ex){
        return ex.getMessage();
    }

}
