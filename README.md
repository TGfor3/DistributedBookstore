# DistributedBookstore
Overview: Software designed for a chain of bookstores. Was created using Spring Boot, and is written entirely in Java. Also uses resilience4j, sf4j, and the GSON library. All data is stored in JPA repository and written to disk.

Build Tool: Gradle 7

Abilities: The CRUD operations, to handle book inventory, as well as information about each boosktore.

Batch requests

Leader election

Distributed logging

Resilience

The HUB: The hub coordinates and informs each server of any newly created server. The hub is also responsible for everything relating to the leader. It randomly chooses the leader. Also pings the leader periodically to check system health. If the leader is down, the hub randomly assigns a new one.

Setup:

Prior to start up of each server, environment variables (PORT, ADDRESS, HUB) must be set with a domain available to your network.
Run the HubApplication first.
Run a DatastoreApplication and POST a server before running another.
Repeat step 3 without overlapping for as many servers as wanted.
Built with @SethJacobs, @BenjyJack, @yolshin

Mentored by @yspotts
