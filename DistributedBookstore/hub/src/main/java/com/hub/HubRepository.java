package com.hub;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HubRepository extends JpaRepository<HubEntry, Long>{

    List<HubEntry> findByServerAddress(String address);

}
