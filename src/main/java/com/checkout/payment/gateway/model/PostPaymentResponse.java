package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Response returned after a payment is processed or retrieved")
public class PostPaymentResponse {

  @Schema(description = "Unique payment identifier", example = "a3b8f042-1e16-4f0a-a8f0-421e16df4201")
  private UUID id;

  @Schema(description = "Payment status: AUTHORIZED, DECLINED, or REJECTED")
  private PaymentStatus status;

  @Schema(description = "Last four digits of the card number", example = "8877")
  @JsonProperty("card_number_last_four")
  private int cardNumberLastFour;

  @Schema(description = "Card expiry month (1–12)", example = "4")
  @JsonProperty("expiry_month")
  private int expiryMonth;

  @Schema(description = "Card expiry year (4-digit)", example = "2025")
  @JsonProperty("expiry_year")
  private int expiryYear;

  @Schema(description = "ISO 4217 currency code", example = "USD")
  private String currency;

  @Schema(description = "Payment amount in the smallest currency unit (e.g. cents)", example = "100")
  private int amount;


  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public int getCardNumberLastFour() {
    return cardNumberLastFour;
  }

  public void setCardNumberLastFour(int cardNumberLastFour) {
    this.cardNumberLastFour = cardNumberLastFour;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  @Override
  public String toString() {
    return "PostPaymentResponse{" +
        "id=" + id +
        ", status=" + status +
        ", cardNumberLastFour=" + cardNumberLastFour +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
