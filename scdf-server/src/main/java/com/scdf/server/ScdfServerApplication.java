package com.scdf.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.dataflow.server.EnableDataFlowServer;

@SpringBootApplication
@EnableDataFlowServer
public class ScdfServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScdfServerApplication.class, args);
	}
}
