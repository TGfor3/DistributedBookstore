package com.ds.datastore;

import org.springframework.stereotype.Component;

/**
 * The leader is the server designated to handle batch requests.
 */
@Component
public class Leader {

    /**
     * This is the server ID of the leader.
     */
    private Long leader;

    /**
     * @return the server ID of the leader
     */
    public Long getLeader() {
        return leader;
    }

    /**
     * Sets the server ID of the leader.
     * @param leader the server ID of the leader
     */
    public void setLeader(Long leader) {
        this.leader = leader;
    }

}
