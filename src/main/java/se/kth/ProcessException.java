package se.kth;

/**
 * If a process is unable to complete, then this will store the output of the process as well as code.
 */
class ProcessException extends Exception {
    String stdout;
    String stderr;
    int code;

    ProcessException(String stdout, String stderr, int code, String message) {
        super(message);
        assert code != 0;
        this.stderr = stderr;
        this.stdout = stdout;
        this.code = code;
    }

    @Override
    public String toString() {
        return "ProcessException{" +
                "stdout='" + stdout + '\'' +
                ", stderr='" + stderr + '\'' +
                ", code=" + code +
                '}';
    }
}