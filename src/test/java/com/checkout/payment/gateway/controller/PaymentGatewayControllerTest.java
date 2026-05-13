package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(PaymentGatewayController.class)
class PaymentGatewayControllerTest {

  public static final String SERVICE_API = "/api/v1/payments";
  @Autowired
  private MockMvc mvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private PaymentGatewayService paymentGatewayService;

  @Test
  void getPaymentById_whenExists_returnsOk() throws Exception {
    GetPaymentResponse payment = buildGetPaymentResponse(PaymentStatus.AUTHORIZED);
    when(paymentGatewayService.getPaymentById(payment.getId())).thenReturn(payment);

    mvc.perform(MockMvcRequestBuilders.get(SERVICE_API + "/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(payment.getId().toString()))
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.card_number_last_four").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiry_month").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiry_year").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void getPaymentById_whenNotFound_returns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(paymentGatewayService.getPaymentById(id)).thenThrow(new PaymentNotFoundException(id));

    mvc.perform(MockMvcRequestBuilders.get(SERVICE_API + "/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Payment not found with ID: " + id));
  }

  @Test
  void processPayment_withValidRequest_returnsCreated() throws Exception {
    PostPaymentRequest request = buildValidRequest();
    PostPaymentResponse response = buildPaymentResponse(PaymentStatus.AUTHORIZED);
    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class))).thenReturn(response);

    mvc.perform(MockMvcRequestBuilders.post(SERVICE_API)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void processPayment_withValidRequest_declinedResponse() throws Exception {
    PostPaymentRequest request = buildValidRequest();
    PostPaymentResponse response = buildPaymentResponse(PaymentStatus.DECLINED);
    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class))).thenReturn(response);

    mvc.perform(MockMvcRequestBuilders.post(SERVICE_API)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"));
  }

  @Test
  void processPayment_withInvalidRequest_returns400() throws Exception {
    PostPaymentRequest request = buildValidRequest();
    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class)))
        .thenThrow(new PaymentValidationException(List.of("Card number must be 14-19 numeric digits")));

    mvc.perform(MockMvcRequestBuilders.post(SERVICE_API)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0]").value("Card number must be 14-19 numeric digits"));
  }

  @Test
  void processPayment_whenBankReturns400_returns502() throws Exception {
    PostPaymentRequest request = buildValidRequest();
    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class)))
        .thenThrow(new EventProcessingException("Bank rejected the payment request: 400 BAD_REQUEST"));

    mvc.perform(MockMvcRequestBuilders.post(SERVICE_API)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("Bank rejected the payment request: 400 BAD_REQUEST"));
  }

  @Test
  void processPayment_whenBankReturns503_returns502() throws Exception {
    PostPaymentRequest request = buildValidRequest();
    when(paymentGatewayService.processPayment(any(PostPaymentRequest.class)))
        .thenThrow(new EventProcessingException("Bank is unavailable: 503 SERVICE_UNAVAILABLE"));

    mvc.perform(MockMvcRequestBuilders.post(SERVICE_API)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("Bank is unavailable: 503 SERVICE_UNAVAILABLE"));
  }

  @Test
  void getPaymentById_withNonUuidId_returns400() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get(SERVICE_API + "/5"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid value '5' for parameter 'id'"));
  }

  private PostPaymentRequest buildValidRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(12);
    request.setExpiryYear(2027);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  private GetPaymentResponse buildGetPaymentResponse(PaymentStatus status) {
    GetPaymentResponse response = new GetPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setStatus(status);
    response.setCardNumberLastFour(4321);
    response.setExpiryMonth(12);
    response.setExpiryYear(2027);
    response.setCurrency("USD");
    response.setAmount(100);
    return response;
  }

  private PostPaymentResponse buildPaymentResponse(PaymentStatus status) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setStatus(status);
    response.setCardNumberLastFour(4321);
    response.setExpiryMonth(12);
    response.setExpiryYear(2027);
    response.setCurrency("USD");
    response.setAmount(100);
    return response;
  }
}
