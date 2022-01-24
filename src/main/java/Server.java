import lombok.AllArgsConstructor;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class Server {

    private static final List<String> VALID_PATHS = List.of(
            "/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js",
            "/links.html", "/forms.html", "/classic.html",
            "/events.html", "/events.js"
    );

    private static final int THREADS_COUNT = 64;

    private final int port;
    private final Map<String, Handler> handlerMap;

    public Server(int port) {
        this.port = port;
        this.handlerMap = new ConcurrentHashMap<>();
    }

    public void start() {

        final var executorService = Executors.newFixedThreadPool(THREADS_COUNT);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try {
                    final var socket = serverSocket.accept();
                    executorService.submit(getServerTask(socket));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        handlerMap.put(method + " " + path, handler);
    }

    private Runnable getServerTask(Socket socket) {
        return () -> {
            try {
                handleConnection(socket);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
    }

    private void handleConnection(Socket socket) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            final var request = Request.getRequest(in);

            if (request == null) {
                return;
            }

            final var handler = handlerMap.get(request.getMethod() + " " + request.getPath());

            if (handler == null) {
                if (!VALID_PATHS.contains(request.getPath())) {
                    makeNotFoundResponse(out);
                } else {
                    makeResponseWithContent(out, request.getPath());
                }
            } else {
                handler.handle(request, out);
            }
            out.flush();
        }
    }

    private void makeNotFoundResponse(BufferedOutputStream out) throws IOException {
        final var responseData = new ResponseData(404, "Not Found", null, 0);
        writeStatusAndHeaders(responseData, out);
    }

    private void writeStatusAndHeaders(ResponseData data, BufferedOutputStream out) throws IOException {

        final var builder = new StringBuilder();

        builder
                .append("HTTP/1.1 ")
                .append(data.getCode())
                .append(" ")
                .append(data.getDescription())
                .append("\r\n");

        if (data.getContentType() != null) {

            builder
                    .append("Content-Type: ")
                    .append(data.getContentType())
                    .append("\r\n");
        }

        builder
                .append("Content-Length: ")
                .append(data.getContentLength())
                .append("\r\n")
                .append("Connection: close\r\n")
                .append("\r\n");

        out.write(builder.toString().getBytes());
    }

    private void makeResponseWithContent(BufferedOutputStream out, String path) throws IOException {

        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        if (path.equals("/classic.html")) {
            final var template = Files.readString(filePath);
            final byte[] content = template.replace("{time}", LocalDateTime.now().toString()).getBytes();

            final var responseData = new ResponseData(200, "OK", mimeType, content.length);

            writeStatusAndHeaders(responseData, out);
            out.write(content);

        } else {
            final var length = Files.size(filePath);
            final var responseData = new ResponseData(200, "OK", mimeType, length);

            writeStatusAndHeaders(responseData, out);
            Files.copy(filePath, out);
        }
    }
}