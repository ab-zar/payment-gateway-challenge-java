package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankClient {

  private static final Logger LOG = LoggerFactory.getLogger(BankClient.class);
  private final RestTemplate restTemplate;
  private final String bankUrl;

  public BankClient(RestTemplate restTemplate, @Value("${bank.simulator.url}") String bankUrl) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  public BankPaymentResponse processPayment(BankPaymentRequest request) {

    LOG.info("Sending payment request to bank at {}", bankUrl);

    try {

      ResponseEntity<BankPaymentResponse> response =
          restTemplate.postForEntity(bankUrl, request, BankPaymentResponse.class);
      LOG.info("Bank response status: {}", response.getStatusCode());

      return response.getBody();

      // should not happen as we are validating the payment info before submitting
    } catch (HttpClientErrorException e) {
      LOG.error("Bank rejected the payment request with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
      throw new EventProcessingException("Bank rejected the payment request: " + e.getStatusCode());

      // when bank is unavailable or returns 5xx, we want to return a 502 Bad Gateway to the client, as the issue is with the upstream service
    } catch (HttpServerErrorException e) {
      LOG.error("Bank unavailable, status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
      throw new EventProcessingException("Bank is unavailable: " + e.getStatusCode());

      // covers connection failures
    } catch (ResourceAccessException e) {
      LOG.error("Could not reach bank: {}", e.getMessage());
      throw new EventProcessingException("Could not reach bank: " + e.getMessage());
    }
  }
}
