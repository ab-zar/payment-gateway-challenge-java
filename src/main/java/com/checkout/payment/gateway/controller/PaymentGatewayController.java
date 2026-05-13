package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Operations for processing and retrieving card payments")
@RestController("api")
@RequestMapping("api/v1/payments")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @Operation(summary = "Get payment by ID", description = "Retrieves a previously processed payment by its unique identifier.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Payment found",
          content = @Content(schema = @Schema(implementation = GetPaymentResponse.class))),
      @ApiResponse(responseCode = "404", description = "Payment not found",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "400", description = "Invalid value for parameter 'id' — must be a valid UUID",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
  })
  @GetMapping("/{id}")
  public ResponseEntity<GetPaymentResponse> getPaymentById(
      @Parameter(description = "Unique payment identifier (UUID)", required = true)
      @PathVariable UUID id) {
    return ResponseEntity.ok(paymentGatewayService.getPaymentById(id));
  }

  @Operation(summary = "Process a payment", description = "Submits a card payment to the bank. Returns AUTHORIZED, DECLINED based on validation and bank response.")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Payment processed (AUTHORIZED or DECLINED)",
          content = @Content(schema = @Schema(implementation = PostPaymentResponse.class))),
      @ApiResponse(responseCode = "400", description = "Invalid payment request or malformed request body",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "500", description = "Unexpected server error",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
      @ApiResponse(responseCode = "502", description = "Bank returned an unexpected response",
          content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<PostPaymentResponse> processPayment(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "Card payment details", required = true,
          content = @Content(schema = @Schema(implementation = PostPaymentRequest.class)))
      @RequestBody PostPaymentRequest request) {
    PostPaymentResponse response = paymentGatewayService.processPayment(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
