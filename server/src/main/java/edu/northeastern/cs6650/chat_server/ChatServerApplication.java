package edu.northeastern.cs6650.chat_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Chat Server.
 */
@SpringBootApplication
public class ChatServerApplication {

	/**
	 * Main method to run the Spring Boot application.
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(ChatServerApplication.class, args);
	}

}
