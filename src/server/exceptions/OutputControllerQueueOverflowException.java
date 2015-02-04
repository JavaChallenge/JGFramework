package server.exceptions;

/**
 * Created by alilotfi on 1/23/15.
 */
public class OutputControllerQueueOverflowException extends RuntimeException {
    public OutputControllerQueueOverflowException() {
        super();
    }

    public OutputControllerQueueOverflowException(String s) {
        super(s);
    }
}
