package com.ds.datastore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByStoreID(Long storeID);

}
