package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "Request body for processing a card payment")
public class PostPaymentRequest implements Serializable {

  @Schema(description = "Full card number (leading zeroes preserved). Masked in logs.", example = "2222405343248877")
  @JsonProperty("card_number") // Full card number in order to process the payment, but we will mask it in logs and responses for security reasons.
  private String cardNumber; // Card number might contain leading zeroes, so it should be String

  @Schema(description = "Card expiry month (1–12)", example = "4")
  @JsonProperty("expiry_month")
  private Integer expiryMonth; // To distinguish between 0 and null

  @Schema(description = "Card expiry year (4-digit)", example = "2025")
  @JsonProperty("expiry_year")
  private Integer expiryYear; // To distinguish between 0 and null

  @Schema(description = "ISO 4217 currency code. Supported: USD, GBP, EUR.", example = "USD")
  private String currency;

  @Schema(description = "Payment amount in the smallest currency unit (e.g. cents)", example = "100")
  private Integer amount; // To distinguish between 0 and null. Holds up to 2.1 billion. Integer should be enough for any realistic transaction

  @Schema(description = "Card CVV / security code (leading zeroes preserved)", example = "123")
  private String cvv; // Might have leading zeroes

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public Integer getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(Integer expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public Integer getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(Integer expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }
  // We still need to mask the card number in the logs.
  @Override
  public String toString() {
    String maskedCard = (cardNumber != null && cardNumber.length() >= 4)
        ? "************" + cardNumber.substring(cardNumber.length() - 4)
        : "****";
    return "PostPaymentRequest{" +
        "cardNumber='" + maskedCard + '\'' +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
