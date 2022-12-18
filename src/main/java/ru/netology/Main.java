package ru.netology;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        final int port = 9999;
        Server server = new Server();
        server.run(port);
    }
}


