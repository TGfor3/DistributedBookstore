package com.ds.datastore;

public class BookStoreNotFoundException extends RuntimeException {

    public BookStoreNotFoundException(Long id) {
        super("Could not find book store " + id);
    }

}