package com.ds.datastore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface BookStoreRepository extends JpaRepository<BookStore, Long> {

    Optional<BookStore> findByServerId(Long serverID);

}
