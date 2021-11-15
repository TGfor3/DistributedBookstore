package com.hub;


import java.util.HashMap;

public class ServerHub {
    private final HashMap<Long, String> serverToHTTP;
    public ServerHub() {
        serverToHTTP = new HashMap<>();
    }
    public void addServer(Long id, String address){
        serverToHTTP.put(id, address);
    }
    public boolean removeServer(Long id){
        return serverToHTTP.remove(id) != null;
    }
    public String getAddress(Long id){
        return serverToHTTP.get(id);
    }
    public HashMap<Long, String> getMap(){
        return this.serverToHTTP;
    }
}
