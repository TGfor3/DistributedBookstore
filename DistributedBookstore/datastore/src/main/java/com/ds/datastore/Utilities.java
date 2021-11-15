package com.ds.datastore;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.Builder;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

@Component
public class Utilities {

    private Logger logger = LoggerFactory.getLogger(Utilities.class);

    /**
     * Creates connection from current server to specified server.
     * @param address the address of the specified server
     * @param jso the body of the request
     * @param serverAddress the address of the current server
     * @param id the server ID of the current server
     * @param requestType the specified request type (POST, GET, PUT, DELETE)
     * @return response from the specified server
     * @throws Exception if there is an error
     */
    @Retry(name = "retry")
    public HttpResponse<String> createConnection(String address, JsonObject jso, String serverAddress, Long id, String requestType) throws Exception {
        logger.info("Started connection");
        String requestID;
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if(attr != null){
            HttpServletRequest currentReq = attr.getRequest();
            requestID = currentReq.getAttribute("requestID").toString();
        }else{
            requestID = String.valueOf(UUID.randomUUID());
        }
        logger.info("Handling Request {}", requestID);
        Gson gson = new Gson();
        String json = gson.toJson(jso);
        Builder builder = HttpRequest.newBuilder()
                .uri(new URI(address))
                .headers("Content-Type", "application/json;charset=UTF-8");
        if(requestType.equals("GET")) {
                builder = builder
                        .setHeader("id", String.valueOf(id))
                        .timeout(Duration.ofSeconds(4))
                        .GET();
        }else if(requestType.equals("POST")){
            builder = builder.POST(HttpRequest.BodyPublishers.ofString(json));
        }else if(requestType.equals("PUT")){
            builder = builder.PUT(HttpRequest.BodyPublishers.ofString(json));
        }else if (requestType.equals("DELETE")) {
            builder = builder.DELETE();
        }
        HttpRequest request = builder.setHeader("requestID", requestID)
                .setHeader("referer", serverAddress)
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        logger.info("Request sent with requestID {}", requestID);
        if(response.statusCode() > 299){
            logger.warn("{} received, {} failed", response.statusCode(), requestType);
            throw new RuntimeException();
        }
        return response;
    }

    /**
     * @see #createConnection(String, JsonObject, String, Long, String)
     * @return Optional containing response
     */
    @Retry(name = "retry")
    @CircuitBreaker(name = "#root.args[0]", fallbackMethod = "fallback")
    public Optional<HttpResponse<String>> createConnectionCircuitBreaker(String address, JsonObject jso, String serverAddress, Long id, String requestType) throws Exception {
        HttpResponse<String> response = createConnection(address, jso, serverAddress, id, requestType);
        return Optional.ofNullable(response);
    }

    /**
     * In cases where the CircuitBreaker fails, this method is called.
     * @see #createConnectionCircuitBreaker(String, JsonObject, String, Long, String)
     * @return empty Optional
     */
    private Optional<HttpResponse<String>> fallback(String address, JsonObject jso, String serverAddress, Long id, String requestType, CallNotPermittedException e) {
        logger.info("Entered fall back");
        return Optional.empty();
    }
}
