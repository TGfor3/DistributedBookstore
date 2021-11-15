package com.ds.datastore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.gson.JsonObject;

import javax.persistence.*;

@JsonIgnoreProperties(value = {"store"})
@Entity
public class Book {

    @Column(name = "id")
    private @Id @GeneratedValue Long id;
    @Column(name = "author")
    private String author;
    @Column(name = "category")
    private String category;
    @Column(name = "title")
    private String title;
    @Column(name = "price")
    private double price = -1;
    @Column(name = "description")
    private String description;
    @Column(name = "language")
    private Language language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "books")
    private BookStore store;
    private Long storeID;

    public Book() {}
    public Book(Book book)
    {
        this.language=book.getLanguage();
        this.title=book.getTitle();
        this.author=book.getAuthor();
        this.description=book.getDescription();
        this.storeID = book.getStoreID();
        this.price = book.getPrice();
        this.category = book.getCategory();
        this.id = book.getId();
    }

    public Book(JsonObject jObj){
        this.language=(!jObj.get("language").isJsonNull() ? Language.valueOf(jObj.get("language").getAsString()) :null);
        this.title = (!jObj.get("title").isJsonNull() ? jObj.get("title").getAsString():null);
        this.author = (!jObj.get("author").isJsonNull() ? jObj.get("author").getAsString():null);
        this.description = (!jObj.get("description").isJsonNull() ? jObj.get("description").getAsString():null);
        this.storeID = (!jObj.get("storeID").isJsonNull() ? jObj.get("storeID").getAsLong():null);
        this.price = (!jObj.get("price").isJsonNull() ? jObj.get("price").getAsDouble():-1.0);
        this.category = (!jObj.get("category").isJsonNull() ? jObj.get("category").getAsString() : null);
        this.id = (!jObj.get("id").isJsonNull() ? jObj.get("id").getAsLong():null);
    }

    public void setStoreID(Long storeID) {
        this.storeID = storeID;
    }
    public Long getStoreID() {
        return storeID;
    }

    public BookStore getStore() {
        return store;
    }
    public void setStore(BookStore store) {
        this.store = store;
    }

    public String getAuthor() {
        return author;
    }
    public void setAuthor(String author){
        this.author = author;
    }

    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public Language getLanguage() {
        return language;
    }
    public void setLanguage(Language language) {
        this.language = language;
    }

    public double getPrice() {
        return price;
    }
    public void setPrice(double price) {
        this.price = price;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getId() {
        return id;
    }

    public JsonObject makeJson() {
        JsonObject jso = new JsonObject();
        if(this.getAuthor() != null)
        {
            jso.addProperty("author", this.getAuthor());
        }
        if(this.getTitle() != null)
        {
            jso.addProperty("title", this.getTitle());
        }
        if(this.getCategory() != null)
        {
            jso.addProperty("category", this.getCategory());
        }
        jso.addProperty("price", this.getPrice());
        if(this.getDescription() != null)
        {
            jso.addProperty("description", this.getDescription());
        }
        if(this.getLanguage() != null)
        {
            jso.addProperty("language", String.valueOf(this.getLanguage()));
        }
        if (this.id != null) {
            jso.addProperty("id", this.id);
        }
        return jso;
    }

}
