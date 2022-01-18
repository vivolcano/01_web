import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Request {

    private final String method;
    private final String path;
    private final String headers;
    private final String body;

}
