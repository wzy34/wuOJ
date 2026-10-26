package dev.wuoj.sandbox;

/** The sandbox itself failed (Docker, the runner, the file system); the attempt is retried. */
public class SandboxException extends RuntimeException {

    public SandboxException(String message) {
        super(message);
    }

    public SandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
