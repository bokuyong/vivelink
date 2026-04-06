package com.vibelink.sender;

public class ControllerTarget {

    private final String name;
    private final String profile;
    private final String host;

    public ControllerTarget(String name, String profile, String host) {
        this.name = name;
        this.profile = profile;
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public String getDisplayLabel() {
        return name + " (" + profile + ")";
    }
}
