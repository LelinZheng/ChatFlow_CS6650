package edu.northeastern.cs6650.chat_server.model;
import java.util.List;

/**
 * Represents a standardized error response returned by the API.
 * <p>
 * This class is typically used to provide structured error information
 * to clients when a request fails due to validation errors, bad input,
 * or server-side issues.
 */
public class ErrorResponse {
  private String status;
  private String errorCode;
  private String message;
  private List<String> details;

  /**
   * Constructor for ErrorResponse.
   * @param errorCode code representing the type of error
   * @param message message describing the error
   * @param details details of the error
   */
  public ErrorResponse(String errorCode, String message, List<String> details) {
    this.status = "ERROR";
    this.errorCode = errorCode;
    this.message = message;
    this.details = details;
  }

  /**
   * Get the status of the error response.
   * @return status string
   */
  public String getStatus() {
    return status;
  }

  /**
   * Get the error code.
   * @return error code string
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * Get the error message.
   * @return error message string
   */
  public String getMessage() {
    return message;
  }

  /**
   * Get the details of the error.
   * @return list of error details
   */
  public List<String> getDetails() {
    return details;
  }
}
