package com.checkout.payment.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class BankClientTest {

  @Mock
  private RestTemplate restTemplate;

  private BankClient bankClient;

  private static final String BANK_URL = "http://localhost:8080/payments/";

  @BeforeEach
  void setUp() {
    bankClient = new BankClient(restTemplate, BANK_URL);
  }

  @Test
  void processPayment_successfulResponse_returnsBody() {
    BankPaymentRequest request = new BankPaymentRequest("2222405343248877", "12/2027", "GBP", 100, "123");
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    when(restTemplate.postForEntity(eq(BANK_URL), eq(request), eq(BankPaymentResponse.class)))
        .thenReturn(ResponseEntity.ok(bankResponse));

    BankPaymentResponse result = bankClient.processPayment(request);

    assertThat(result.isAuthorized()).isTrue();
  }

  @Test
  void processPayment_bankReturns400_throwsEventProcessingException() {
    BankPaymentRequest request = new BankPaymentRequest("2222405343248877", "12/2027", "GBP", 100, "123");
    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> bankClient.processPayment(request))
        .isInstanceOf(EventProcessingException.class)
        .hasMessageContaining("Bank rejected the payment request");
  }

  @Test
  void processPayment_bankReturns503_throwsEventProcessingException() {
    BankPaymentRequest request = new BankPaymentRequest("2222405343248870", "12/2027", "GBP", 100, "123");
    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> bankClient.processPayment(request))
        .isInstanceOf(EventProcessingException.class)
        .hasMessageContaining("Bank is unavailable");
  }

  @Test
  void processPayment_networkFailure_throwsEventProcessingException() {
    BankPaymentRequest request = new BankPaymentRequest("2222405343248877", "12/2027", "GBP", 100, "123");
    when(restTemplate.postForEntity(anyString(), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenThrow(new ResourceAccessException("Connection refused"));

    assertThatThrownBy(() -> bankClient.processPayment(request))
        .isInstanceOf(EventProcessingException.class)
        .hasMessageContaining("Could not reach bank");
  }
}






