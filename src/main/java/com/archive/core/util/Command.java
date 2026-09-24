package com.archive.core.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class Command {

    // DTO to hold the result of a parse
    private final String action;
    private final Map<String, String> flags = new HashMap<>();
    private final List<String> args = new ArrayList<>();

    public Command(String action) {
        this.action = action.toLowerCase();
    }

    public void addFlag(String key, String value) {
        flags.put(key.toLowerCase(), value);
    }

    public String getFlag(String key) {
        return flags.get(key.toLowerCase());
    }

    public boolean hasFlag(String key) {
        return flags.containsKey(key.toLowerCase());
    }

    public void addArg(String arg) {
        args.add(arg);
    }

    public List<String> getArgs() {
        return args;
    }
}
