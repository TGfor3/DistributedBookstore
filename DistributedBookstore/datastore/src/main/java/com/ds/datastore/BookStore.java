package com.ds.datastore;

import javax.persistence.*;
import java.util.List;
import java.util.ArrayList;

@Entity
public class BookStore {

    @Id
    @Column(name = "server_id")
    private Long serverId;
    @Column(name = "name")
    private String name = "";
    @Column(name = "phone")
    private String phone = "";
    @Column(name = "address")
    private String streetAddress = "";

    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Book> books = new ArrayList<>();

    public String getName(){
        return name;
    }
    public void setName(String storeName){
        this.name = storeName;
    }

    public List<Book> getBooks() {
        return books;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }
}
