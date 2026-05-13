package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);
  /* static list of currencies (no more than 3, according to the spec). Enum is not necessary.
   Jackson can deserialize to enum but allows less control over error messages
   Plus it would require a custom deserializer to allow case-insensitive matching, which is not worth the effort for 3 values */
  private static final List<String> SUPPORTED_CURRENCIES = List.of("USD", "GBP", "EUR");

  private final PaymentsRepository paymentsRepository;
  private final BankClient bankClient;

  public PaymentGatewayService(PaymentsRepository paymentsRepository, BankClient bankClient) {
    this.paymentsRepository = paymentsRepository;
    this.bankClient = bankClient;
  }

  public GetPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    PostPaymentResponse stored = paymentsRepository.get(id).orElseThrow(() -> new PaymentNotFoundException(id));
    return toGetPaymentResponse(stored);
  }

  public PostPaymentResponse processPayment(PostPaymentRequest request) {
    validate(request);

    String expiryDate = request.getExpiryDate();
    BankPaymentRequest bankRequest = new BankPaymentRequest(
        request.getCardNumber(),
        expiryDate,
        request.getCurrency(),
        request.getAmount(),
        request.getCvv()
    );

    BankPaymentResponse bankResponse = bankClient.processPayment(bankRequest);

    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setStatus(bankResponse.isAuthorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED);
    response.setCardNumberLastFour(Integer.parseInt(
        request.getCardNumber().substring(request.getCardNumber().length() - 4)));
    response.setExpiryMonth(request.getExpiryMonth());
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency());
    response.setAmount(request.getAmount());

    paymentsRepository.add(response);
    LOG.info("Payment {} processed with status {}", response.getId(), response.getStatus());
    return response;
  }

  /* There's no need for dedicated validator bean. No overengineering.
   I also decided not to introduce Bean validations in the model itself because I'd lose this nice error collection.
   Bean validation still won't allow me to perform cross-field validation (expiry month/year combination)
   without a custom validator, which is not worth the effort for this simple case. */
  private void validate(PostPaymentRequest request) {
    List<String> errors = new ArrayList<>();

    // Card number: required, 14-19 numeric characters
    if (request.getCardNumber() == null || request.getCardNumber().isBlank()) {
      errors.add("Card number is required");
    } else if (!request.getCardNumber().matches("\\d{14,19}")) {
      errors.add("Card number must be 14-19 numeric digits");
    }

    // Expiry month: required, 1-12
    boolean expiryMonthValid = request.getExpiryMonth() != null;
    if (!expiryMonthValid) {
      errors.add("Expiry month is required");
    } else if (request.getExpiryMonth() < 1 || request.getExpiryMonth() > 12) {
      errors.add("Expiry month must be between 1 and 12");
      expiryMonthValid = false;
    }

    // Expiry year: required; combination with month must be in the future
    if (request.getExpiryYear() == null) {
      errors.add("Expiry year is required");
    } else if (expiryMonthValid) {
      YearMonth expiry = YearMonth.of(request.getExpiryYear(), request.getExpiryMonth());
      if (!expiry.isAfter(YearMonth.now())) {
        errors.add("Card expiry date must be in the future");
      }
    }

    // Currency: required, must be one of supported values. Checking if the length == 3 is redundant
    if (request.getCurrency() == null || request.getCurrency().isBlank()) {
      errors.add("Currency is required");
    } else if (!SUPPORTED_CURRENCIES.contains(request.getCurrency().toUpperCase())) {
      errors.add("Currency must be one of: " + String.join(", ", SUPPORTED_CURRENCIES));
    }

    // Amount: required, positive integer
    if (request.getAmount() == null) {
      errors.add("Amount is required");
    } else if (request.getAmount() <= 0) {
      errors.add("Amount must be a positive integer");
    }

    // CVV: required, 3-4 numeric characters
    if (request.getCvv() == null || request.getCvv().isBlank()) {
      errors.add("CVV is required");
    } else if (!request.getCvv().matches("\\d{3,4}")) {
      errors.add("CVV must be 3-4 numeric digits");
    }

    if (!errors.isEmpty()) {
      throw new PaymentValidationException(errors);
    }
  }

  /* Cast stored model to dto. Technically redundant, since all the fields are 1 to 1.
   But here it represents a clear separation between the internal storage model and the API response model,
   which allows more flexibility in the future if we want to change one without affecting the other.
   In a more complicated case it would be good to have a dedicated mapper class, but for this simple case it's not worth the effort.
   */
  private GetPaymentResponse toGetPaymentResponse(PostPaymentResponse source) {
    GetPaymentResponse response = new GetPaymentResponse();
    response.setId(source.getId());
    response.setStatus(source.getStatus());
    response.setCardNumberLastFour(source.getCardNumberLastFour());
    response.setExpiryMonth(source.getExpiryMonth());
    response.setExpiryYear(source.getExpiryYear());
    response.setCurrency(source.getCurrency());
    response.setAmount(source.getAmount());
    return response;
  }
}
