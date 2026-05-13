package com.checkout.payment.gateway.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentGatewayIntegrationTest {

  private static WireMockServer wireMock;

  @Autowired
  private TestRestTemplate restTemplate;

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @DynamicPropertySource
  static void bankUrl(DynamicPropertyRegistry registry) {
    registry.add("bank.simulator.url", () -> wireMock.baseUrl() + "/payments");
  }

  @BeforeEach
  void resetWireMock() {
    wireMock.resetAll();
  }

  // ---- POST /api/v1/payments ----

  @Test
  void processPayment_authorizedCard_returns201WithAuthorizedStatus() {
    stubBank("""
        {"authorized": true, "authorization_code": "0bb07405-6d44-4b50-a14f-7ae0beff13ad"}
        """);

    ResponseEntity<String> response = performPost("/api/v1/payments", authorizedCardBody());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).contains("\"status\":\"Authorized\"");
    assertThat(response.getBody()).contains("\"card_number_last_four\":8877");
    assertThat(response.getBody()).contains("\"currency\":\"GBP\"");
    assertThat(response.getBody()).contains("\"amount\":100");
    assertThat(response.getBody()).contains("\"id\"");
  }

  @Test
  void processPayment_declinedCard_returns201WithDeclinedStatus() {
    stubBank("""
        {"authorized": false}
        """);

    ResponseEntity<String> response = performPost("/api/v1/payments", declinedCardBody());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).contains("\"status\":\"Declined\"");
    assertThat(response.getBody()).contains("\"card_number_last_four\":8872");
  }

  @Test
  void processPayment_authorizedCard_canBeRetrievedById() {
    stubBank("""
        {"authorized": true, "authorization_code": "abc123"}
        """);

    ResponseEntity<String> postResponse = performPost("/api/v1/payments", authorizedCardBody());
    assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    String id = extractId(postResponse.getBody());
    ResponseEntity<String> getResponse = restTemplate.getForEntity("/api/v1/payments/" + id, String.class);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody()).contains("\"id\":\"" + id + "\"");
    assertThat(getResponse.getBody()).contains("\"status\":\"Authorized\"");
    assertThat(getResponse.getBody()).contains("\"card_number_last_four\":8877");
  }

  @Test
  void processPayment_invalidRequest_returns400WithAllErrors() {
    ResponseEntity<String> response = performPost("/api/v1/payments", invalidBody());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("\"errors\"");
    assertThat(response.getBody()).contains("Card number");
    assertThat(response.getBody()).contains("Currency");
    assertThat(response.getBody()).contains("Amount");
    assertThat(response.getBody()).contains("CVV");
  }

  @Test
  void processPayment_bankUnavailable_returns502() {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse().withStatus(503)));

    ResponseEntity<String> response = performPost("/api/v1/payments", bankUnavailableCardBody());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody()).contains("\"message\"");
    assertThat(response.getBody()).contains("Bank is unavailable");
  }

  // ---- GET /api/v1/payments/{id} ----

  @Test
  void getPaymentById_unknownId_returns404() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/api/v1/payments/00000000-0000-0000-0000-00000000000", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("\"message\"");
  }

  @Test
  void getPaymentById_Id_type_mismatch() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/api/v1/payments/00000000", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("\"message\"");
  }

  // ---- helpers ----

  private void stubBank(String responseBody) {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)));
  }

  private ResponseEntity<String> performPost(String path, String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private String authorizedCardBody() {
    return """
        {
          "card_number": "2222405343248877",
          "expiry_month": 12,
          "expiry_year": 2027,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }""";
  }

  private String declinedCardBody() {
    return """
        {
          "card_number": "2222405343248872",
          "expiry_month": 6,
          "expiry_year": 2027,
          "currency": "GBP",
          "amount": 100,
          "cvv": "456"
        }""";
  }

  private String bankUnavailableCardBody() {
    return """
        {
          "card_number": "2222405343248870",
          "expiry_month": 3,
          "expiry_year": 2027,
          "currency": "USD",
          "amount": 250,
          "cvv": "789"
        }""";
  }

  private String invalidBody() {
    return """
        {
          "card_number": "123",
          "expiry_month": 13,
          "expiry_year": 2020,
          "currency": "JPY",
          "amount": -1,
          "cvv": "9"
        }""";
  }

  private String extractId(String responseBody) {
    // parse "id":"<uuid>" from JSON
    int start = responseBody.indexOf("\"id\":\"") + 6;
    int end = responseBody.indexOf("\"", start);
    return responseBody.substring(start, end);
  }
}





