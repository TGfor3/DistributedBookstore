package com.hub;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * This is the I/O class for the Hub Application
 * This class connects and tracks all of the servers' information
 * Also searches, sets, and check on the leader of the network
 */
@RestController
@EnableScheduling
public class HubController {

    private final ServerHub hub;
    private final HubRepository repository;
    private String leader;
    private Logger logger = LoggerFactory.getLogger(HubController.class);

    /**
     * Sets up the initial steps of the Hub Application
     * @param repository the file designated the Spring Boot as the resilient repo storing the network's servers' info
     */
    public HubController(HubRepository repository){
        this.repository = repository;
        this.hub = new ServerHub();
        leader = findAndSendLeader();
        logger.info("Hub initialized");
    }

    /**
     * Only once the hub has started can it repopulate the map in memory from the resilient repo
     */
    @PostConstruct
    private void mapSetUp(){
        List<HubEntry> servers = repository.findAll();
        for (HubEntry entry : servers){
            this.hub.addServer(entry.getId(), entry.getServerAddress());
            logger.info("{} added to map", entry.getServerAddress());
        }
    }

    /**
     * Searches the network for a viable leader and sets the leader for the network
     * @return String containing the ID of the leader
     */
    protected String findAndSendLeader(){
        leader = null;
        for(Long id: hub.getMap().keySet()){
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(this.hub.getAddress(id) + "/ping"))
                        .headers("Content-Type", "application/json;charset=UTF-8")
                        .header("referer", "HUB")
                        .GET()
                        .build();
                HttpResponse<String> response = HttpClient.newBuilder()
                        .build()
                        .send(request, HttpResponse.BodyHandlers.ofString());
                if(response.body().equals("true")){
                    leader = String.valueOf(id);
                    sendLeader();
                    logger.info("{} is now the leader", leader);
                    return leader;
                }
            }
            catch(Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Add new server to the network
     * @param json String in JSON format which contains the new server's information
     * @return Long which is the newly generated ID to be given to the server
     * @throws Exception for any failures that may occur when sending HTTP Requests
     */
    @RateLimiter(name = "DDoS-stopper")
    @PostMapping("/hub")
    protected Long addServer(@RequestBody String json) throws Exception {
        String address = parseAddressFromJsonString(json);
        HubEntry server = new HubEntry();
        server.setServerAddress(address);
        repository.save(server);
        address = address + server.getId();
        server.setServerAddress(address);
        repository.save(server);
        Long serverId = server.getId();
        String newServerInfo = storeServerInfoInString(serverId, address);
        this.hub.addServer(serverId, address);
        addNewServerToAllServers(newServerInfo);
        if(leader == null){
            leader = String.valueOf(serverId);
        }
        sendLeader();
        logger.info("{} has been added to the network", address);
        return serverId;
    }

    /**
     * Sends out updated leader to all servers in the network
     * @throws Exception for any failures that may occur when sending HTTP Requests
     */
    private void sendLeader() throws Exception {
        for(String address : hub.getMap().values()){
            retrySend(address);
        }
        logger.info("Leader sent");
    }

    @Retry(name = "retry")
    private void retrySend(String address) throws Exception{
        HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(address + "/leader"))
                    .PUT(HttpRequest.BodyPublishers.ofString(leader))
                    .header("referer", "HUB")
                    .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Performs health checks on the leader
     * @throws Exception for incorrect URI formation
     */
    @Scheduled(fixedDelay = 60000)
    protected void randomlyCheck() throws Exception {
        if(this.leader != null){
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.hub.getAddress(Long.parseLong(leader)) + "/ping"))
                    .headers("Content-Type", "application/json;charset=UTF-8")
                    .header("referer", "HUB")
                    .timeout(Duration.ofMillis(3000))
                    .GET()
                    .build();
            HttpResponse<String> response = null;
            try {
                response = HttpClient.newBuilder()
                        .build()
                        .send(request, HttpResponse.BodyHandlers.ofString());
            }catch(Exception e){
                findAndSendLeader();
            }
            if(!response.body().equals("true")){
                findAndSendLeader();
            }
        }else{
            findAndSendLeader();
        }
    }

    /**
     * @return JSON formatted HashMap of the network, e.g. {ID: address}
     */
    @RateLimiter(name = "DDoS-stopper")
    @GetMapping("/hub")
    protected String getMap(){
        Gson gson = new Gson();
        return gson.toJson(this.hub.getMap());
    }

    /**
     * Returns the current leader if it exists
     * If there isn't a leader & the server calling this method has an ID assigned to it, make it the leader and return
     * If neither is true, return null, as there isn't a leader
     * @param id String of the requesting server's ID
     * @return leader if there is one set, otherwise null
     */
    @RateLimiter(name = "DDoS-stopper")
    @GetMapping("/hub/leader")
    protected String getLeader(@RequestHeader(name = "id") String id){
        if(leader == null && !id.equals("null")){
            leader = id;
        }
        logger.info("Current leader: {}", leader);
        return leader;
    }

    /**
     * Parses a server's address from a JSON formatted String
     * @param json String in JSON format containing a server's address
     * @return String of the server's address
     */
    private String parseAddressFromJsonString(String json){
        JsonParser parser = new JsonParser();
        JsonObject jsonObject = parser.parse(json).getAsJsonObject();
        return jsonObject.get("address").getAsString();
    }

    /**
     * Convert a server's info into a String formatted as a JSON
     * @param id server's ID in the network
     * @param address server's address
     * @return String formatted as a JSON containing the server's info
     */
    private String storeServerInfoInString(Long id, String address){
        Gson gson = new Gson();
        JsonObject newServerAsJson = new JsonObject();
        newServerAsJson.addProperty("id", id);
        newServerAsJson.addProperty("address", address);
        return gson.toJson(newServerAsJson);
    }

    /**
     * Send a new server's info to all of the server's on the network
     * @param serverInfo String formatted as a JSON containing the server's info
     * @throws Exception for any failures that may occur when sending HTTP Requests
     */
    private void addNewServerToAllServers(String serverInfo) throws Exception {
        for (Long id : this.hub.getMap().keySet()){
            //Send the newly created server to the pre-existing servers
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.hub.getAddress(id)))
                    .headers("Content-Type", "application/json;charset=UTF-8")
                    .header("referer", "HUB")
                    .POST(HttpRequest.BodyPublishers.ofString(serverInfo))
                    .build();
            HttpClient.newBuilder()
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("New server {} sent to {}", serverInfo, this.hub.getAddress(id));
        }
    }

    /**
     * Update the address on a server already existing on the network and send to all of the server's the update
     * @param json String formatted as a JSON containing the server's info
     * @throws Exception for any failures that may occur when sending HTTP Requests
     */
    @RateLimiter(name = "DDoS-stopper")
    @PutMapping("/hub")
    protected void updateAddress(@RequestBody String json) throws Exception {
        JsonParser parser = new JsonParser();
        JsonObject jsonObject = parser.parse(json).getAsJsonObject();
        Long id = jsonObject.get("id").getAsLong();
        String address = jsonObject.get("address").getAsString();
        this.hub.addServer(id, address);
        HubEntry entry = repository.getById(id);
        entry.setServerAddress(address);
        repository.save(entry);
        addNewServerToAllServers(json);
        logger.info("{}'s address has changed to {}", id, address);
    }

    /**
     * Remove a server from the network permanently
     * @param serverID Long of the server's ID
     * @throws Exception for any failures that may occur when sending HTTP Requests
     */
    @RateLimiter(name = "DDoS-stopper")
    @DeleteMapping("/hub/{serverID}")
    protected void removeServerFromNetwork(@PathVariable Long serverID) throws Exception {
        if(!this.hub.removeServer(serverID)) return;
        repository.deleteById(serverID);
        for (Long id : this.hub.getMap().keySet()){
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.hub.getAddress(id)))
                    .headers("Content-Type", "application/json;charset=UTF-8")
                    .header("referer", "HUB")
                    .DELETE()
                    .build();
            HttpClient.newBuilder()
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
        }
        if(serverID.equals(Long.parseLong(leader))){
            findAndSendLeader();
        }
        logger.info("{} has been removed from the network", serverID);
    }
}
