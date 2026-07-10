package de.omnistreamforce.core;

public enum EventType {
    NORMAL,
    ERROR,
    WARNING,
    INFO;

    public boolean isError() {
        return this == ERROR;
    }
}