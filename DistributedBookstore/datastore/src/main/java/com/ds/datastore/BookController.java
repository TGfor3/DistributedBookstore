package com.ds.datastore;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

import javax.servlet.http.HttpServletRequest;

@RestController
public class BookController {

    private final BookRepository repository;
    private final BookModelAssembler assembler;
    private final BookStoreRepository storeRepository;
    private ServerMap map;
    private Leader leader;
    Logger logger = LoggerFactory.getLogger(BookController.class);
    private Utilities utilities;
    @Value("${application.baseUrl}")
    private String url;

    public BookController(BookRepository repository, BookModelAssembler assembler, BookStoreRepository storeRepository, ServerMap map, Leader leader, Utilities utilities){
        this.repository = repository;
        this.assembler = assembler;
        this.storeRepository = storeRepository;
        this.map = map;
        this.leader = leader;
        this.utilities = utilities;
    }

    /** 
     * Post a new book
     * @param book The book to be posted
     * @param storeID The ID of the store to which the book is being posted
     * @return ResponseEntity<EntityModel<Book>>
     * If the specified bookstore is not the one on this server, a redirect to the proper server is returned
     * @throws Exception If the specified store does not exist in this network
     */
    @RateLimiter(name = "DDoS-stopper")
    @PostMapping("/bookstores/{storeID}/books")
    protected ResponseEntity<EntityModel<Book>> newBook(@RequestBody Book book, @PathVariable Long storeID) throws Exception{
        try{
            BookStore store = checkStore(storeID);
            book.setStoreID(storeID);
            book.setStore(store);
            EntityModel<Book> entityModel = assembler.toModel(repository.save(book));
            logger.info("Book {} by {} has been added", book.getTitle(), book.getAuthor());
            return ResponseEntity
                .created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(entityModel);
        }catch(BookStoreNotFoundException e){
            if (this.map.containsKey(storeID)) {
                return redirect(storeID);
            }else{
                logger.error("The specified bookstore {} does not exist", storeID, e);
                throw e;
            }
        }     
    }

    /** 
     * Post the same book to multiple bookstores
     * @param book The book being posted
     * @param id A list of store the books are to be posted to
     * @param request The request from the client, contains useful information like the RequestID
     * @return CollectionModel<EntityModel<Book>>
     * @throws Exception if the connection fails
     */
    @RateLimiter(name = "DDoS-stopper")
    @PostMapping("/bookstores/book")
    protected CollectionModel<EntityModel<Book>> oneBookToManyStores(@RequestBody Book book, @RequestParam List<String> id, HttpServletRequest request) throws Exception{
        List<EntityModel<Book>> entityList = new ArrayList<>();
        if (!amILeader()) {
            String address = this.map.get(this.leader.getLeader());
            address = removeIDNum(address) + "book?id=" + String.join(",", id);
            HttpResponse<String> response = utilities.createConnection(address, book.makeJson(), this.url, null, "POST");
            if(response.statusCode() != 200){
                logger.warn("Server {} was not reached", leader.getLeader());
                throw new RuntimeException("Could not connect to " + address);
            }
            JsonObject jso = new JsonParser().parse(response.body()).getAsJsonObject();
            JsonArray bookArray = jso.getAsJsonObject("_embedded").getAsJsonArray("bookList");
            for (JsonElement element: bookArray) {
                Book newBook = new Book(element.getAsJsonObject());
                entityList.add(assembler.toModel(newBook));
            }
            logger.info("Batch request successfully executed by {}", leader.getLeader());
        }else{
            logger.info("Leader handling request {}", request.getAttribute("requestID"));
            for(String storeId : id) {
                book.setStoreID(Long.parseLong(storeId));
                if(!this.map.containsKey(Long.parseLong(storeId))) continue;
                String address = this.map.get(Long.parseLong(storeId)) + "/books";
                Optional<HttpResponse<String>> optional = utilities.createConnectionCircuitBreaker(address, book.makeJson(), this.url, null, "POST");
                if(optional.isEmpty()){
                    continue;
                }
                HttpResponse<String> response = optional.get();
                JsonParser parser = new JsonParser();
                JsonObject jo = parser.parse(response.body()).getAsJsonObject();
                entityList.add(assembler.toModel(new Book(jo)));
            }
            if(!entityList.isEmpty()){
                logger.info("Batch request successfully handled");
            }
        }
        return CollectionModel.of(entityList, linkTo(methodOn(BookController.class).oneBookToManyStores(null, null, null)).withSelfRel());
    }

    
    /** 
     * Post batch of books to specific bookstores
     * @param json Collection of books being posted
     * @param request The request from the client, contains useful information like the RequestID
     * @return CollectionModel<EntityModel<Book>>
     * @throws Exception if the connection fails
     */
    @RateLimiter(name = "DDoS-stopper")
    @PostMapping("/bookstores/books")
    protected CollectionModel<EntityModel<Book>> multipleToMultiple(@RequestBody BookArray json, HttpServletRequest request) throws Exception{
        String requestID = request.getAttribute("requestID").toString();
        if(!amILeader()){
            return multipleToLeader(json, requestID);
        }
        List<EntityModel<Book>> entityModelList = new ArrayList<>();
        for (Book book: json.getBooks()) {
            if(book.getStoreID() == null || !this.map.containsKey(book.getStoreID())) {
                continue;
            }
            JsonObject jso = book.makeJson();
            jso.addProperty("storeID", book.getStoreID());
            Optional<HttpResponse<String>> optional = utilities.createConnectionCircuitBreaker(this.map.get(book.getStoreID()) + "/books", jso, this.url, null, "POST");
            if(!optional.isEmpty()) {
                HttpResponse<String> response = optional.get();
                JsonParser parser = new JsonParser();
                JsonObject jo = parser.parse(response.body()).getAsJsonObject();
                entityModelList.add(assembler.toModel(new Book(jo)));
            }
        }
        logger.info("Batch of multiple to multiple completed");
        return CollectionModel.of(entityModelList, linkTo(methodOn(BookController.class)).withSelfRel());
    }

    /** 
     * Forward batch request to the leader
     * @param array Collection of books being posted
     * @param requestID The RequestID for logging purposes
     * @return CollectionModel<EntityModel<Book>>
     * @throws Exception if the connection fails
     */
    private CollectionModel<EntityModel<Book>> multipleToLeader(BookArray array, String requestID) throws Exception{
        String address = this.map.get(leader.getLeader());
        address = removeIDNum(address) + "books";
        JsonArray jsonArray = new JsonArray();
        for (Book book : array.getBooks()) {
            JsonObject jso = book.makeJson();
            jso.addProperty("storeID", book.getStoreID());
            jsonArray.add(jso);
        }
        JsonObject elementedArray = new JsonObject();
        elementedArray.add("books", jsonArray);
        HttpResponse<String> response = utilities.createConnection(address, elementedArray, this.url, null, "POST");
        logger.info("Request {} forwarded to leader", requestID);
        if(response.statusCode() != 200){
            logger.warn("{} status code received", response.statusCode());
            throw new RuntimeException("Could not connect to " + address);
        }
        logger.info("Books sent successfully");
        JsonObject jso = new JsonParser().parse(response.body()).getAsJsonObject();
        JsonArray bookArray = jso.getAsJsonObject("_embedded").getAsJsonArray("bookList");
        ArrayList<EntityModel<Book>> entityModels = new ArrayList<>();
        for (JsonElement element: bookArray) {
            Book book = new Book(element.getAsJsonObject());
            entityModels.add(assembler.toModel(book));
        }
        logger.info("Batch of multiple to multiple completed by " + leader.getLeader());
        return  CollectionModel.of(entityModels, linkTo(methodOn(BookController.class)).withSelfRel());
    }

    /** 
     * Get one book from the specified boosktore
     * @param bookId The book to return
     * @param storeID The store which contains the book
     * @param request The request from the client, contains useful information like the RequestID
     * @return ResponseEntity<EntityModel<Book>>
     * If the specified bookstore is not the one on this server, a redirect to the proper server is returned
     * @throws Exception if the bookstore can't be found
     */
    @RateLimiter(name = "DDoS-stopper")
    @GetMapping("/bookstores/{storeID}/books/{bookId}")
    protected ResponseEntity<EntityModel<Book>> one(@PathVariable Long bookId, @PathVariable Long storeID, HttpServletRequest request) throws Exception{
        String requestID = request.getAttribute("requestID").toString();
        try{
            checkStore(storeID);
            Book book = checkBook(bookId);
            EntityModel<Book> entityModel = assembler.toModel(book);
            logger.info("Request {} handled", requestID);
            return ResponseEntity.created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri())
            .body(entityModel);
        }catch(BookStoreNotFoundException e){
            if (this.map.containsKey(storeID)) {
                logger.info("Redirecting request {}", requestID);
                return redirectWithId(bookId, storeID);
            }else{
                logger.error("Bookstore not found", e);
                throw e;
            }
        }
    }

    /** 
     * @param storeID The bookstore to get the books from
     * @param id A list of books to get
     * If not provided, will assume that all books contained in the specified store are being requested
     * @param request The request from the client, contains useful information like the RequestID
     * @return ResponseEntity<CollectionModel<EntityModel<Book>>>
     * If the specified bookstore is not the one on this server, a redirect to the proper server is returned
     * @throws Exception if the bookstore can't be found
     */
    @RateLimiter(name = "DDoS-stopper")
    @GetMapping("/bookstores/{storeID}/books")
    protected ResponseEntity<CollectionModel<EntityModel<Book>>> all(@PathVariable Long storeID, @RequestParam(required = false) List<String> id, HttpServletRequest request) throws Exception{
        String requestID = request.getAttribute("requestID").toString();
        List<EntityModel<Book>> booksAll;
        try {
            checkStore(storeID);
            if(id != null) {
                return getAllSpecific(storeID, id, requestID);
            }
            booksAll = repository.findByStoreID(storeID)
                .stream()
                .map(assembler::toModel)
                .collect(Collectors.toList());
            logger.info("Request {} successfully handled", requestID);
            return ResponseEntity.ok(CollectionModel.of(booksAll, linkTo(methodOn(BookController.class).all(storeID, null, null)).withSelfRel()));
        }catch (BookStoreNotFoundException e) {
            if (this.map.containsKey(storeID)) {
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.map.get(storeID) + "/books");
                URI uri = new URI(builder.toUriString());
                logger.info("Redirecting request {}", requestID);
                return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(uri).header("requestID", requestID).build();
            }else{
                logger.warn("Bookstore not found", e);
                throw e;
            }
        }     
    }

    /** 
     * @param storeID The bookstore to get the books from
     * @param id A list of books to get
     * @param requestID The RequestID for logging purposes
     * @return ResponseEntity<CollectionModel<EntityModel<Book>>>
     * Returns a list of the specifiec books, if they are present in this bookstore
     * @throws Exception
     */
    private ResponseEntity<CollectionModel<EntityModel<Book>>> getAllSpecific(Long storeID, List<String> id, String requestID) throws Exception{
        List<EntityModel<Book>> entModelList = new ArrayList<>();
        for(String bookId : id) {
            Long parsedId = Long.parseLong(bookId);
            if(repository.findById(parsedId).isEmpty()) {
                continue;
            }
            entModelList.add(assembler.toModel(repository.findById(parsedId).get()));
        }
        logger.info("Request {} successfully handled", requestID);
        return ResponseEntity.ok(CollectionModel.of(entModelList, linkTo(methodOn(BookController.class).all(storeID, null, null)).withSelfRel()));
    }

    /** 
     * @param newBook The updated book
     * @param id ID of the book being updated
     * @param storeID Bookstore which contains the book to be updated
     * @param request The request from the client, contains useful information like the RequestID
     * @return ResponseEntity<EntityModel<Book>>
     * If the specified bookstore is not the one on this server, a redirect to the proper server is returned
     * @throws Exception if the bookstore can't be found
     */
    @RateLimiter(name = "DDoS-stopper")
    @PutMapping("/bookstores/{storeID}/books/{id}")
    protected ResponseEntity<EntityModel<Book>> updateBook(@RequestBody Book newBook, @PathVariable Long id, @PathVariable Long storeID, HttpServletRequest request) throws Exception{
        String requestID = request.getAttribute("requestID").toString();
        try{
            checkStore(storeID);
            Book updatedBook = repository.findById(id)
                .map(book -> {
                    if(newBook.getAuthor() != null) book.setAuthor(newBook.getAuthor());
                    if(newBook.getPrice() != -1)  book.setPrice(newBook.getPrice());
                    if(newBook.getCategory() != null) book.setCategory(newBook.getCategory());
                    if(newBook.getDescription() != null) book.setDescription(newBook.getDescription());
                    if(newBook.getLanguage() != null) book.setLanguage(newBook.getLanguage());
                    if(newBook.getTitle() != null) book.setTitle(newBook.getTitle());
                    logger.info("Book {} updated", id);
                    return repository.save(book);
                })
                .orElseThrow(() -> new BookNotFoundException(id));
            EntityModel<Book> entityModel = assembler.toModel(updatedBook);
            return ResponseEntity.created(entityModel.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(entityModel);
        }catch(BookStoreNotFoundException e){
            if (this.map.containsKey(storeID)) {
                logger.info("Redirecting request {}", requestID);
                return redirectWithId(id, storeID);
            }else{
                logger.warn("Bookstore not found", e);
                throw e;
            }
        }
    }

    /** 
     * @param id ID of the book being deleted
     * @param storeID Bookstore which contains the intended book
     * @param request The request from the client, contains useful information like the RequestID
     * @return ResponseEntity<EntityModel<Book>>
     * If the specified bookstore is not the one on this server, a redirect to the proper server is returned
     * @throws Exception if the bookstore can't be found
     */
    @RateLimiter(name = "DDoS-stopper")
    @DeleteMapping("/bookstores/{storeID}/books/{id}")
    protected ResponseEntity<EntityModel<Book>> deleteBook(@PathVariable Long id, @PathVariable Long storeID, HttpServletRequest request) throws Exception{
        String requestID = request.getAttribute("requestID").toString();
        try{
            checkStore(storeID);
            checkBook(id);
            repository.deleteById(id);
            logger.info("Book {} permanently terminated", id);
            return ResponseEntity.noContent().build();
        }catch(BookStoreNotFoundException e){
            if (this.map.containsKey(storeID)) {
                logger.info("Redirecting request {}", requestID);
                return redirectWithId(id, storeID);
            }else{
                logger.warn("Bookstore not found", e);
                throw e;
            }
        }
    }

    /** 
     * Checks if this server is currently the leader
     * @return boolean
     */
    private boolean amILeader() {
        if(this.storeRepository.findAll().isEmpty()) {
            return false;
        }
        return this.storeRepository.findAll().get(0).getServerId().equals(this.leader.getLeader());
    }

    /** 
     * Check if the bookstore on this server is the one being requested
     * @param storeID
     * @return BookStore
     */
    private BookStore checkStore(Long storeID){
        return storeRepository.findById(storeID).orElseThrow(() -> new BookStoreNotFoundException(storeID));
    }

    /** 
     * Check if the repository contains the specified book
     * @param id
     * @return Book
     */
    private Book checkBook(Long id){
        return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }

    /** 
     * Remove the id from the end of the address 
     * @param address
     * @return String
     */
    private String removeIDNum(String address){
        return address.substring(0,address.lastIndexOf("/") + 1);
    }

    /** 
     * Create a redirect to the specified server
     * @param storeId
     * @return ResponseEntity<EntityModel<Book>>
     * @throws Exception
     */
    private ResponseEntity<EntityModel<Book>> redirect(Long storeId) throws Exception{
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.map.get(storeId) + "/books");
        URI uri = new URI(builder.toUriString());
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(uri).build();
    }

    /** 
     * Create a redirect to the specified server, with the desired storeID appended to the address
     * @param bookId
     * @param storeId
     * @return ResponseEntity<EntityModel<Book>>
     * @throws Exception
     */
    private ResponseEntity<EntityModel<Book>> redirectWithId(Long bookId, Long storeId) throws Exception{
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.map.get(storeId) + "/books/" + bookId);
        URI uri = new URI(builder.toUriString());
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(uri).build();
    }
}
