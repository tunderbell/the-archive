package com.archive.core.util;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CommandParser {
    
    // This regex finds words or phrases inside quotes
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");

    public Command parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new Command("help");
        }

        List<String> tokens = tokenize(input);
        
        // If the first token starts with --, the user likely forgot the action
        if (tokens.get(0).startsWith("--")) {
        Command helpCmd = new Command("help");
        helpCmd.addFlag("error", "No action specified");
        return helpCmd;
        }

        // The first word is the action (e.g., add, list, delete)
        Command command = new Command(tokens.get(0));

        // Parse the rest for flags (e.g., --title "Monster")
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if (token.startsWith("--")) {
                String key = token.substring(2); // Remove the '--'
                
                // If there's a next token and it's not another flag, it's the value
                if (i + 1 < tokens.size() && !tokens.get(i + 1).startsWith("--")) {
                    command.addFlag(key, tokens.get(i + 1));
                    i++; // Skip the value in the next iteration
                } else {
                    // It's a boolean flag (e.g., --adult)
                    command.addFlag(key, "true");
                }
            }
        }

        return command;
    }

    private List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN_PATTERN.matcher(input);
        while (m.find()) {
            if (m.group(1) != null) {
                // Quoted with "
                tokens.add(m.group(1));
            } else if (m.group(2) != null) {
                // Quoted with '
                tokens.add(m.group(2));
            } else {
                // Unquoted
                tokens.add(m.group());
            }
        }
        return tokens;
    }
}