package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Controller for BookStores allows CRUD Operations for a Book Store in the network
 */
@RestController
public class BookStoreController {

    private final BookModelAssembler bookModelAssembler;
    private final BookStoreModelAssembler bookStoreModelAssembler;
    private final BookRepository bookRepository;
    private final BookStoreRepository storeRepository;
    private ServerMap map;
    private Long id = null;
    private Leader leader;
    private Logger logger = LoggerFactory.getLogger(BookStoreController.class);
    private Utilities utilities;

    @Value("${application.baseUrl}")
    private String url;

    @Value("${hub.url}")
    private String hubUrl;

    public BookStoreController(BookStoreModelAssembler bookStoreModelAssembler, BookModelAssembler bookModelAssembler, BookRepository bookRepository, BookStoreRepository storeRepository, ServerMap map, Leader leader, Utilities utilities) {
        this.bookStoreModelAssembler = bookStoreModelAssembler;
        this.bookModelAssembler = bookModelAssembler;
        this.bookRepository = bookRepository;
        this.storeRepository = storeRepository;
        this.map = map;
        this.leader = leader;
        this.utilities = utilities;
    }

    /**
     * If restarted a server gets the new and updated map back from the hub
     * and gets the current leader from the hub
     * Then checks to see if current address is same as the address originally registered with the hub and makes
     * the necessary changes
     *  @throws Exception if can not connect to the hub
     */
    @PostConstruct
    public void restartChangedOrNew() throws Exception {
        try {
            this.map.setMap(reclaimMap());
        }catch (Exception e) {
            logger.error("Unable to get map from HUB", e);
        }
        List<BookStore> bookStoreList = storeRepository.findAll();
        if(!bookStoreList.isEmpty()) {
            BookStore bookStore = bookStoreList.get(0);
            this.id = bookStore.getServerId();
            String serverAddress = this.map.get(this.id);
            if(!serverAddress.equals(url + "/bookstores/" + this.id)) {
                registerWithHub();
            }
        }
        Long currentLeader = getLeader();
        if(currentLeader != null){
            this.leader.setLeader(currentLeader);
        }
        logger.info("Server initialized");
    }

    /**
     * Helper method which tells the hub the Book stores id and address and allows it to be connected to the network
     * @throws Exception if can not create connection
     */
    private void registerWithHub() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("id", this.id);
        json.addProperty("address", this.url + "/bookstores/" + this.id);
        utilities.createConnection(hubUrl, json, this.url, this.id, "PUT");
        logger.info("Server {} connected to network at {}", this.id, this.url);
    }

    /**
     * @return The map of server addresses and their id's from the hub
     * @throws Exception if can not connect to the hub
     */
    private HashMap<Long, String> reclaimMap() throws Exception {
        HttpResponse<String> response = utilities.createConnection(hubUrl, null, this.url, id, "GET");
        String json = response.body();
        Gson gson = new Gson();
        Type type = new TypeToken<HashMap<Long, String>>(){}.getType();
        logger.info("Map reclaimed");
        return gson.fromJson(json, type);
    }

    /**
     * @return the id of the leader i.e the server to handle batch requests
     * @throws Exception if can not connect to the hub to get the leader
     */
    private Long getLeader() throws Exception {
        HttpResponse<String> response = utilities.createConnection(hubUrl + "/leader", null, this.url, id, "GET");
        logger.info("Leader found. Mission Accomplished");
        if(response.body().equals("")){
            return null;
        }
        return Long.parseLong(response.body());
    }

    /**
     * sets the leader  method would be called by the hub
     */
    @RateLimiter(name = "DDoS-stopper")
    @PutMapping("/bookstores/{storeID}/leader")
    protected void setLeader(@RequestBody Long leader)
    {
        this.leader.setLeader(leader);
    }

    /**
     * @param bookStore to add to the server
     * @return entity model of the Book store sent to the server
     * @throws Exception if fails to post the server to the hub
     */
    @RateLimiter(name = "DDoS-stopper")
    @PostMapping("/bookstores")
    protected ResponseEntity<EntityModel<BookStore>> newBookStore(@RequestBody BookStore bookStore) throws Exception {
        if (this.id != null) {
            logger.warn("Bookstore already exists on this server with ID {}", this.id);
            return ResponseEntity.status(HttpStatus.ALREADY_REPORTED).build();
        }
        EntityModel<BookStore> entityModel = postToHub(bookStore);
        logger.info("Current map: " + map.getMap().toString());
        logger.info("Store posted");
        this.leader.setLeader(getLeader());
        return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
    }

    /**
     * Method for hub to call when new server was added to the network
     * @param json with an id and address of a new server to be added to the map of servers in the network
     */
    @RateLimiter(name = "DDoS-stopper")
    @PostMapping("/bookstores/{id}")
    protected void addServer(@RequestBody String json, @PathVariable String id) {
        JsonObject jso = new JsonParser().parse(json).getAsJsonObject();
        Long givenID = jso.get("id").getAsLong();
        String address = jso.get("address").getAsString();
        this.map.put(givenID, address);
        logger.info("{} has joined the network", givenID);
    }

    /**
     * Method to get one Book store from the server
     * @param storeID of the Book store to get
     * @return an entity model of the Book store
     * @throws Exception if no Book store with that ID found
     */
    @RateLimiter(name = "DDoS-stopper")
    @GetMapping("/bookstores/{storeID}")
    protected ResponseEntity<EntityModel<BookStore>> one(@PathVariable Long storeID, HttpServletRequest request) throws Exception {
        String requestID = request.getAttribute("requestID").toString();
        try{
            BookStore bookStore = storeRepository.findByServerId(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
            EntityModel<BookStore> entityModel = bookStoreModelAssembler.toModel(bookStore);
            logger.info("Request {} successfully handled", requestID);
            return ResponseEntity
                    .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                    .body(entityModel);
        }catch (BookStoreNotFoundException e){
            if(this.map.containsKey(storeID)){
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.map.get(storeID));
                URI uri = new URI(builder.toUriString());
                logger.info("Redirecting request {}", requestID);
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).header("requestID", requestID).location(uri).build();
            }else{
                logger.warn("Bookstore not found", e);
                throw e;
            }
        }
    }

    /**
     * @param id of Book stores to get
     * @return entity model of Book stores with the given IDs
     * @throws Exception if can not find server with that ID
     */
    @RateLimiter(name = "DDoS-stopper")
    @GetMapping("/bookstores")
    protected CollectionModel<EntityModel<BookStore>> getBookStores(@RequestParam(required = false) List<String> id) throws Exception {
        List<EntityModel<BookStore>> entModelList = new ArrayList<>();
        if(id == null) {
            for (Long storeID : this.map.keySet()) {
                try{
                    entModelList.add(getAndParseBookStore(this.map.get(storeID)));
                }catch(Exception ignored){
                    logger.warn("Server {} was not reached", storeID);
                }
            }
        }else{
            for(String storeID : id) {
                Long parsedId = Long.parseLong(storeID);
                String address = this.map.get(parsedId);
                if(address == null) {
                    continue;
                }
                try{
                    entModelList.add(getAndParseBookStore(address));
                }catch(Exception ignored){
                    logger.warn("Server {} was not reached", storeID);
                }
            }
        }
        return CollectionModel.of(entModelList, linkTo(methodOn(BookStoreController.class).getBookStores(null)).withSelfRel());
    }

    /**
     * @param address of Book store to get
     * @return entity model of Book store
     * @throws Exception if can not connect
     */
    private EntityModel<BookStore> getAndParseBookStore(String address) throws Exception{
        HttpResponse<String> response = utilities.createConnection(address, null, this.url, id, "GET");
        String requestID = response.request().headers().firstValue("requestID").get();
        logger.info("Request {} handled", requestID);
        JsonParser parser = new JsonParser();
        JsonObject jso = parser.parse(response.body()).getAsJsonObject();
        BookStore store = new BookStore();
        store.setServerId((jso.get("serverId") != null ? jso.get("serverId").getAsLong() : null));
        store.setName(!jso.get("name").isJsonNull() ? jso.get("name").getAsString() : null);
        store.setPhone(!jso.get("phone").isJsonNull() ? jso.get("phone").getAsString() : null);
        store.setStreetAddress(!jso.get("streetAddress").isJsonNull() ? jso.get("streetAddress").getAsString() : null);
        //Not including the List of books contained in the store
        return bookStoreModelAssembler.toModel(store);
    }

    /**
     * Gets all the books from specific Book store's
     * @param id of Book store's to get all books from
     * @return entity model of books
     * @throws Exception if can not connect to a Book store with a given ID
     */
    @RateLimiter(name = "DDoS-stopper")
    @GetMapping("/bookstores/books")
    protected CollectionModel<EntityModel<Book>> getAllBooksFromBookStores(@RequestParam (required = false) List<String> id, HttpServletRequest request) throws Exception {
        String requestID = request.getAttribute("requestID").toString();
        if(id == null) {
            List<String> arrayList = new ArrayList<>();
            for(Long storeID : this.map.keySet()) {
                arrayList.add(String.valueOf(storeID));
            }
            id = arrayList;
        }
        List<EntityModel<Book>> entModelList = new ArrayList<>();
        for(String storeID : id) {
            Long parsedId = Long.parseLong(storeID);
            String address = this.map.get(parsedId);
            if(address == null) {
                logger.warn("Server {} does not exist", parsedId);
                continue;
            }
            HttpResponse<String> response = utilities.createConnection(address, null, this.url, this.id, "GET");
            if(response.statusCode() > 299) {
                logger.warn("Server {} was not reached", parsedId);
                continue;
            }
            JsonParser parser = new JsonParser();
            JsonObject jso = parser.parse(response.body()).getAsJsonObject();
            Gson gson = new Gson();
            Type collectionType = new TypeToken<List<Book>>(){}.getType();
            List<Book> books = gson.fromJson(jso.get("books"), collectionType);
            for(Book book : books) {
                entModelList.add(bookModelAssembler.toModel(book));
            }
        }
        logger.info("Request {} handled", requestID);
        return CollectionModel.of(entModelList, linkTo(methodOn(BookStoreController.class).getAllBooksFromBookStores(null, null)).withSelfRel());
    }

    /**
     * Method to update information of a Book store
     * @param newBookStore information to change on the old Book store
     * @param storeID of store to change
     * @return entity model of updated Book store
     */
    @RateLimiter(name = "DDoS-stopper")
    @PutMapping("/bookstores/{storeID}")
    protected BookStore updateBookStore(@RequestBody BookStore newBookStore, @PathVariable Long storeID) {
        String requestID = String.valueOf(UUID.randomUUID());
        logger.info("Request {} received", requestID);
        return storeRepository.findById(storeID)
                .map(bookStore -> {
                    if(!newBookStore.getName().equals("")) bookStore.setName(newBookStore.getName());
                    if(!newBookStore.getPhone().equals("")) bookStore.setPhone(newBookStore.getPhone());
                    if(!newBookStore.getStreetAddress().equals("")) bookStore.setStreetAddress(newBookStore.getStreetAddress());
                    logger.info("Bookstore {} successfully updated", storeID);
                    logger.info("Request {} handled", requestID);
                    return storeRepository.save(bookStore);
                })
                .orElseThrow(() -> new BookStoreNotFoundException(storeID));
    }

    /**
     * Delete a Book store
     * This Method will delete the Book store from its host server
     * as well as remove it from the network and delete any books that were in it
     * @param storeID of Book store to delete
     * @return 204 no content if successful
     * @throws Exception if can not find Book store with that ID
     */
    @RateLimiter(name = "DDoS-stopper")
    @DeleteMapping("/bookstores/{storeID}")
    protected ResponseEntity<EntityModel<BookStore>> deleteBookStore(@PathVariable Long storeID, HttpServletRequest request) throws Exception{
        String requestID = request.getAttribute("requestID").toString();
        try{
            storeRepository.findById(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
            storeRepository.deleteById(storeID);
            this.map.remove(storeID);
            this.id = null;
            HttpResponse<String> response = utilities.createConnection(hubUrl + "/" + storeID, null, this.url, this.id, "DELETE");
            List<Book> books = bookRepository.findByStoreID(storeID);
            for (Book book : books) {
                bookRepository.delete(book);
            }
            requestID = response.request().headers().firstValue("requestID").get();
            logger.info("{} has been permanently deleted", storeID);
            logger.info("Request {} handled", requestID);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }catch (BookStoreNotFoundException e){
            if(this.map.containsKey(storeID)){
                logger.info("Illegal Attempt");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }else{
                logger.warn("Bookstore not found", e);
                throw e;
            }
        }
    }

    /**
     * @param json object with id of store to delete from the map of servers
     */
    @RateLimiter(name = "DDoS-stopper")
    @DeleteMapping("/bookstores/map")
    protected void deleteFromMap(@RequestBody String json){
        JsonObject jso = new JsonParser().parse(json).getAsJsonObject();
        Long id = jso.get("id").getAsLong();
        this.map.remove(id);
        logger.info("{} has been deleted", id);
    }

    /**
     * Method for server health check
     * @return True
     */
    @RateLimiter(name = "DDoS-stopper")
    @GetMapping("/bookstores/{storeID}/ping")
    protected boolean ping() {
        return true;
    }

    /**
     * Helper method to post a Book store and its information to the hub
     * @param bookStore to be added to hub
     * @return entity of Book store to be added
     * @throws Exception if can not connect to HUB
     */
    private EntityModel<BookStore> postToHub(BookStore bookStore) throws Exception {
        JsonObject jso = new JsonObject();
        jso.addProperty("address", this.url + "/bookstores/");
        HttpResponse<String> response = utilities.createConnection(hubUrl, jso, this.url, this.id, "POST");
        bookStore.setServerId(Long.parseLong(response.body()));
        this.id = bookStore.getServerId();
        return bookStoreModelAssembler.toModel(storeRepository.save(bookStore));
    }
}
