import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) {

        final var server = new Server(9999);

        server.addHandler("GET", "/messages", (request, responseStream) -> {
            final var txt = "<h1>GET /messages</h1>\n" +
                    "<div>Path: " + request.getPath() + "</div>" +
                    "<div>Params: " + request.getQueryParams() + "</div>" +
                    "<div>Param key1: " + request.getQueryParam("key1") + "</div>" +
                    "<div>Headers: " + request.getHeaders() + "</div>";

            write(txt, responseStream);
        });

        server.addHandler("POST", "/messages", (request, responseStream) -> {
            final var txt = "<h1>POST /messages</h1>\n" +
                    "<div>Path: " + request.getPath() + "</div>" +
                    "<div>Params: " + request.getQueryParams() + "</div>" +
                    "<div>Param key1: " + request.getQueryParam("key1") + "</div>" +
                    "<div>Headers: " + request.getHeaders() + "</div>" +
                    "<div>Body: " + request.getBody() + "</div>" +
                    "<div>PostParams: " + request.getPostParams() + "</div>" +
                    "<div>PostParam value: " + request.getPostParam("value") + "</div>";

            write(txt, responseStream);
        });

        server.start();
    }

    private static void write(String content, BufferedOutputStream out) {

        final var builder = new StringBuilder();

        builder
                .append("HTTP/1.1 200 OK\r\n" + "Content-Type: text/html\r\n" + "Content-Length: ")
                .append(content.length())
                .append("\r\n")
                .append("Connection: close\r\n")
                .append("\r\n");

        try {
            out.write(builder.toString().getBytes());
            out.write(content.getBytes(StandardCharsets.UTF_8));

            System.out.println(builder);
            System.out.println(content);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
