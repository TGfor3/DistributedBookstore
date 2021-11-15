package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class BookModelAssembler implements RepresentationModelAssembler<Book, EntityModel<Book>> {

    @Override
    public EntityModel<Book> toModel(Book book){
        try {
            return EntityModel.of(book,
                    linkTo(methodOn(BookController.class).one(book.getId(), book.getStoreID(), null)).withSelfRel(),
                    linkTo(methodOn(BookController.class).all(book.getStoreID(), null, null)).withRel("books"));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
