import lombok.Getter;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Getter
public class Request {
    private final String method;
    private final String path;
    private final String headers;
    private final String body;

    private final Map<String, List<String>> queryParams;
    private final Map<String, List<String>> postParams;

    public Request(String method, String path, Map<String, List<String>> queryParams,
                   String headers, String body, Map<String, List<String>> postParams) {
        this.method = method;
        this.path = path;
        this.queryParams = queryParams;
        this.headers = headers;
        this.body = body;
        this.postParams = postParams;
    }

    public List<String> getQueryParam(String name) {
        return queryParams.get(name);
    }

    public List<String> getPostParam(String name) {
        return postParams.get(name);
    }

    static Request getRequest(BufferedReader in) throws IOException {

        final var requestLine = in.readLine();
        final String[] parts = requestLine.split(" ");

        if (parts.length != 3) {
            return null;
        }

        final var headers = new StringBuilder();
        final var body = new StringBuilder();

        var hasBody = false;
        var contentType = "";

        String inputLine = in.readLine();
        int bodyLen = 0;

        while (inputLine.length() > 0) {
            headers.append(inputLine);

            if (inputLine.startsWith("Content-Length: ")) {

                var index = inputLine.indexOf(':') + 1;
                bodyLen = Integer.parseInt(inputLine.substring(index).trim());

                if (bodyLen > 0) {
                    hasBody = true;
                }

            } else if (inputLine.startsWith("Content-Type: ")) {

                var index = inputLine.indexOf(':') + 1;
                contentType = inputLine.substring(index).trim();
            }
            inputLine = in.readLine();
        }

        if (hasBody) {
            final char[] bodyBuff = new char[bodyLen];
            in.read(bodyBuff);
            body.append(bodyBuff);
        }

        final var path = parts[1];
        final var bodyStr = body.toString();

        return new Request(parts[0], getCleanPath(path), getQueryParams(path),
                headers.toString(), bodyStr, getPostParams(bodyStr, contentType));
    }

    private static String getCleanPath(String path) {
        if (path.contains("?")) {
            return path.substring(0, path.indexOf("?"));
        }

        return path;
    }

    private static Map<String, List<String>> getQueryParams(String path) {
        if (!path.contains("?")) {
            return Collections.emptyMap();
        }

        final var params = path.substring(path.indexOf("?") + 1);
        return getParams(params);
    }

    private static HashMap<String, List<String>> getParams(String params) {
        HashMap<String, List<String>> paramsMap = new HashMap<>();

        URLEncodedUtils.parse(params, StandardCharsets.UTF_8)
                .forEach(param -> paramsMap.computeIfAbsent(param.getName(),
                        anything -> new ArrayList<>()).add(param.getValue()));

        return paramsMap;
    }

    private static Map<String, List<String>> getPostParams(String body, String contentType) {
        if (!"application/x-www-form-urlencoded".equals(contentType)) {
            return new HashMap<>();
        }

        return getParams(body);
    }
}