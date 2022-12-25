package ru.netology;

public class Main {
    public static void main(String[] args) {
        final int port = 9999;
        Server server = new Server();
        server.addHandler("GET", "/messages", (request, responseStream) -> {
            server.notFound(responseStream);
        });
        server.addHandler("POST", "/message", (request, responseStream) -> {
            String content = "<html><head></head><body>POST</body></html>\n";
            System.out.println("content " /*content.length()*/);
            responseStream.write(("HTTP/1.1 200 OK\r\n" +
                    "Content-Length: " + content.length() + "\r\n" +
                    "Connection: close\r\n" +
                    "Content-Type: text/html\r\n" +
                    "\r\n" +
                    content).getBytes());
            responseStream.flush();
        });
        server.run(port);
    }
}


