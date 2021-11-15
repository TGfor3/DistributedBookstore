package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class BookStoreModelAssembler implements RepresentationModelAssembler<BookStore, EntityModel<BookStore>> {

    @Override
    public EntityModel<BookStore> toModel(BookStore bookStore) {
        try {
            return EntityModel.of(bookStore,
                    linkTo(methodOn(BookStoreController.class).one(bookStore.getServerId(), null)).withSelfRel(),
                    linkTo(methodOn(BookStoreController.class).getBookStores(null)).withRel("bookstores"));
        } catch (Exception e) {
            return null;
        }
    }

}
