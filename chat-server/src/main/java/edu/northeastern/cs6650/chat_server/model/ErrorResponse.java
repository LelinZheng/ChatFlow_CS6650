package edu.northeastern.cs6650.chat_server.model;
import java.util.List;

public class ErrorResponse {
  private String status;
  private String errorCode;
  private String message;
  private List<String> details;

  public ErrorResponse(String errorCode, String message, List<String> details) {
    this.status = "ERROR";
    this.errorCode = errorCode;
    this.message = message;
    this.details = details;
  }

  public String getStatus() {
    return status;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getMessage() {
    return message;
  }

  public List<String> getDetails() {
    return details;
  }
}
