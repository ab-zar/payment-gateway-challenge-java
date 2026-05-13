package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository paymentsRepository;

  @Mock
  private BankClient bankClient;

  private PaymentGatewayService service;

  @BeforeEach
  void setUp() {
    service = new PaymentGatewayService(paymentsRepository, bankClient);
  }

  // ---- GET by ID ----

  @Test
  void getPaymentById_whenExists_returnsPayment() {
    PostPaymentResponse stored = new PostPaymentResponse();
    UUID id = UUID.randomUUID();
    stored.setId(id);
    stored.setStatus(PaymentStatus.AUTHORIZED);
    stored.setCardNumberLastFour(8877);
    stored.setExpiryMonth(12);
    stored.setExpiryYear(2027);
    stored.setCurrency("GBP");
    stored.setAmount(100);
    when(paymentsRepository.get(id)).thenReturn(Optional.of(stored));

    GetPaymentResponse result = service.getPaymentById(id);

    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(result.getCardNumberLastFour()).isEqualTo(8877);
    assertThat(result.getExpiryMonth()).isEqualTo(12);
    assertThat(result.getExpiryYear()).isEqualTo(2027);
    assertThat(result.getCurrency()).isEqualTo("GBP");
    assertThat(result.getAmount()).isEqualTo(100);
  }

  @Test
  void getPaymentById_whenNotFound_throwsException() {
    UUID id = UUID.randomUUID();
    when(paymentsRepository.get(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPaymentById(id))
        .isInstanceOf(PaymentNotFoundException.class);
  }

  // ---- Process Payment - bank authorized ----

  @Test
  void processPayment_authorized_returnsAuthorizedResponse() {
    PostPaymentRequest request = validRequest();
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    when(bankClient.processPayment(any(BankPaymentRequest.class))).thenReturn(bankResponse);

    PostPaymentResponse result = service.processPayment(request);

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(result.getId()).isNotNull();
    assertThat(result.getCardNumberLastFour()).isEqualTo(8877);
    assertThat(result.getCurrency()).isEqualTo("GBP");
    assertThat(result.getAmount()).isEqualTo(100);
    assertThat(result.getExpiryMonth()).isEqualTo(12);
    assertThat(result.getExpiryYear()).isEqualTo(2027);
    verify(paymentsRepository).add(result);
  }

  @Test
  void processPayment_declined_returnsDeclinedResponse() {
    PostPaymentRequest request = validRequest();
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(false);
    when(bankClient.processPayment(any(BankPaymentRequest.class))).thenReturn(bankResponse);

    PostPaymentResponse result = service.processPayment(request);

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    verify(paymentsRepository).add(result);
  }

  // ---- Validation: card number ----

  @Test
  void processPayment_nullCardNumber_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber(null);

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class)
        .hasMessageContaining("Card number");
    verify(bankClient, never()).processPayment(any());
  }

  @Test
  void processPayment_cardNumberTooShort_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("1234567890123"); // 13 digits

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class);
  }

  @Test
  void processPayment_cardNumberTooLong_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("12345678901234567890"); // 20 digits

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class);
  }

  @Test
  void processPayment_cardNumberWithLetters_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("1234abcd5678ef78");

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class);
  }

  // ---- Validation: expiry month ----

  @Test
  void processPayment_nullExpiryMonth_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(null);

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class)
        .hasMessageContaining("Expiry month");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 13, -1})
  void processPayment_invalidExpiryMonth_throwsValidation(int month) {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(month);

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class);
  }

  // ---- Validation: expiry year / date ----

  @Test
  void processPayment_expiredCard_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setExpiryYear(2020);
    request.setExpiryMonth(1);

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class)
        .hasMessageContaining("future");
  }

  // ---- Validation: currency ----

  @Test
  void processPayment_nullCurrency_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setCurrency(null);

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class)
        .hasMessageContaining("Currency");
  }

  @Test
  void processPayment_unsupportedCurrency_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setCurrency("JPY");

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class);
  }

  // ---- Validation: amount ----

  @Test
  void processPayment_zeroAmount_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setAmount(0);

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class)
        .hasMessageContaining("Amount");
  }

  @Test
  void processPayment_negativeAmount_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setAmount(-50);

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class);
  }

  // ---- Validation: CVV ----

  @Test
  void processPayment_nullCvv_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setCvv(null);

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class)
        .hasMessageContaining("CVV");
  }

  @Test
  void processPayment_cvvTooShort_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setCvv("12");

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class);
  }

  @Test
  void processPayment_cvvWithLetters_throwsValidation() {
    PostPaymentRequest request = validRequest();
    request.setCvv("12a");

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(PaymentValidationException.class);
  }

  @Test
  void processPayment_cvvFourDigits_isValid() {
    PostPaymentRequest request = validRequest();
    request.setCvv("1234");
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    when(bankClient.processPayment(any())).thenReturn(bankResponse);

    PostPaymentResponse result = service.processPayment(request);
    assertThat(result.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
  }

  @Test
  void processPayment_multipleInvalidFields_collectsAllErrors() {
    PostPaymentRequest request = new PostPaymentRequest(); // all fields null/default

    PaymentValidationException ex = (PaymentValidationException) org.junit.jupiter.api.Assertions
        .assertThrows(PaymentValidationException.class, () -> service.processPayment(request));

    assertThat(ex.getErrors())
        .contains("Card number is required")
        .contains("Expiry month is required")
        .contains("Expiry year is required")
        .contains("Currency is required")
        .contains("Amount is required")
        .contains("CVV is required")
        .hasSizeGreaterThan(1);

    verify(bankClient, never()).processPayment(any());
  }

  private PostPaymentRequest validRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877"); // ends in 7 (odd) -> authorized
    request.setExpiryMonth(12);
    request.setExpiryYear(2027);
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }
}






