import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResponseData {

    private final int code;
    private final String description;
    private final String contentType;
    private final long contentLength;
}
