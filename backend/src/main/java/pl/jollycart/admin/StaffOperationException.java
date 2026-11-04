package pl.jollycart.admin;

import org.springframework.http.HttpStatus;

public class StaffOperationException extends RuntimeException {

    private final HttpStatus status;

    public StaffOperationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static StaffOperationException forbidden(String message) {
        return new StaffOperationException(HttpStatus.FORBIDDEN, message);
    }

    public static StaffOperationException notFound(String message) {
        return new StaffOperationException(HttpStatus.NOT_FOUND, message);
    }

    public static StaffOperationException conflict(String message) {
        return new StaffOperationException(HttpStatus.CONFLICT, message);
    }
}
