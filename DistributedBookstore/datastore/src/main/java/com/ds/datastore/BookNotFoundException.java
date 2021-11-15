package com.ds.datastore;

public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(Long id) {
        super("Could not find book " + id);
    }

}