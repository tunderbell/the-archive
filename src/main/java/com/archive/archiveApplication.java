package com.archive;

import com.archive.core.util.Command;
import com.archive.core.util.CommandParser;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;



@SpringBootApplication
public class archiveApplication {
    public static void main(String[] args) {
        SpringApplication.run(archiveApplication.class, args);

        // 1. Capture the context (the "Brain" of the app)
        ConfigurableApplicationContext context = SpringApplication.run(archiveApplication.class, args);

        // Quick Test
        CommandParser parser = context.getBean(CommandParser.class);
        Command cmd = parser.parse("add --manga \"Berserk\" --status ONGOING --adult");
        
        System.out.println("--- PARSER TEST ---");
        System.out.println("Action: " + cmd.getAction());
        System.out.println("Flags: " + cmd.getFlags());
        System.out.println("-------------------");
    }
}