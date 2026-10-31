package com.music.e2e;

/**
 * Settings of the run. The base URL is the API gateway: all requests of the tests go through it.
 */
final class E2eConfig {

    private E2eConfig() {
    }

    static String baseUrl() {
        return System.getProperty("e2e.baseUrl", "http://localhost:8080");
    }

}
