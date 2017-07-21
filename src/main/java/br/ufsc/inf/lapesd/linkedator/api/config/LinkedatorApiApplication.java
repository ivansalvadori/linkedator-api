package br.ufsc.inf.lapesd.linkedator.api.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import javax.ws.rs.ApplicationPath;

@EnableAutoConfiguration
@SpringBootApplication
@ComponentScan("br.ufsc.inf.lapesd")
public class LinkedatorApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkedatorApiApplication.class, args);
    }
} 