package com.mystipixel.royaljoin;

/** A configuration error that is safe and useful to show to an administrator. */
public final class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
