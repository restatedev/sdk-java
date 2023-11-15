package dev.restate.sdk.core;

/** When thrown in a Restate service method, it will complete the invocation with an error. */
public class TerminalException extends RuntimeException {

  /**
   * @see io.grpc.Status.Code
   */
  public enum Code {
    OK(0),
    CANCELLED(1),
    UNKNOWN(2),
    INVALID_ARGUMENT(3),
    DEADLINE_EXCEEDED(4),
    NOT_FOUND(5),
    ALREADY_EXISTS(6),
    PERMISSION_DENIED(7),
    RESOURCE_EXHAUSTED(8),
    FAILED_PRECONDITION(9),
    ABORTED(10),
    OUT_OF_RANGE(11),
    UNIMPLEMENTED(12),
    INTERNAL(13),
    UNAVAILABLE(14),
    DATA_LOSS(15),
    UNAUTHENTICATED(16);

    private final int value;

    Code(int value) {
      this.value = value;
    }

    /** The numerical value of the code. */
    public int value() {
      return value;
    }

    public static Code fromValue(int value) {
      switch (value) {
        case 0:
          return Code.OK;
        case 1:
          return Code.CANCELLED;
        case 2:
          return Code.UNKNOWN;
        case 3:
          return Code.INVALID_ARGUMENT;
        case 4:
          return Code.DEADLINE_EXCEEDED;
        case 5:
          return Code.NOT_FOUND;
        case 6:
          return Code.ALREADY_EXISTS;
        case 7:
          return Code.PERMISSION_DENIED;
        case 8:
          return Code.RESOURCE_EXHAUSTED;
        case 9:
          return Code.FAILED_PRECONDITION;
        case 10:
          return Code.ABORTED;
        case 11:
          return Code.OUT_OF_RANGE;
        case 12:
          return Code.UNIMPLEMENTED;
        case 13:
          return Code.INTERNAL;
        case 14:
          return Code.UNAVAILABLE;
        case 15:
          return Code.DATA_LOSS;
        case 16:
          return Code.UNAUTHENTICATED;
        default:
          return Code.UNKNOWN;
      }
    }
  }

  private final Code code;

  public TerminalException() {
    this.code = Code.UNKNOWN;
  }

  public TerminalException(Code code) {
    this.code = code;
  }

  public TerminalException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public TerminalException(String message) {
    super(message);
    this.code = Code.UNKNOWN;
  }

  public Code getCode() {
    return code;
  }
}
