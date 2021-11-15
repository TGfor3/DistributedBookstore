package com.ds.datastore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@ServletComponentScan(basePackages = "com.ds.datastore")
@SpringBootApplication
public class DatastoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(DatastoreApplication.class, args);
	}
}
