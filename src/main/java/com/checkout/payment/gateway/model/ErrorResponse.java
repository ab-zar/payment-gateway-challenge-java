package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Error response returned when a request fails")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

  @Schema(description = "Human-readable error message (404 responses)", example = "Payment not found")
  private final String message;

  @Schema(description = "List of validation errors (400 responses)", example = "[\"Card number must be between 14 and 19 digits\"]")
  private final List<String> errors;

  public ErrorResponse(String message) {
    this.message = message;
    this.errors = null;
  }

  public ErrorResponse(List<String> errors) {
    this.message = null;
    this.errors = errors;
  }

  public String getMessage() {
    return message;
  }

  public List<String> getErrors() {
    return errors;
  }

  @Override
  public String toString() {
    return "ErrorResponse{message='" + message + "', errors=" + errors + '}';
  }
}
