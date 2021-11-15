package com.ds.datastore;

import java.util.HashMap;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ServerMap {
    private HashMap<Long,String> serverMap;
    public ServerMap(){
        this.serverMap = new HashMap<>();
    }
    public void put(Long id, String address){
        serverMap.put(id,address);
    }
    public String get(Long id){
        return this.serverMap.get(id);
    }
    public HashMap<Long,String> getMap(){
        return this.serverMap;
    }
    public void setMap(HashMap<Long, String> map){
        this.serverMap = map;
    }
    public void remove(Long id){
        this.serverMap.remove(id);
    } 
    public boolean containsKey(Long id){
        return this.serverMap.containsKey(id);
    }
    public Set<Long> keySet(){
        return this.serverMap.keySet();
    }
}
