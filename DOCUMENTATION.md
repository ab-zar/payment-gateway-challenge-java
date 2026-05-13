Aydar Zaripov
-
**Key Design Considerations:**

1. PostPaymentRequest class
   - According to requirements this class needs to have full card number, but the existing file had only last 4 digits
   - Card number can be up to 19 digits long, which is too long for a 32bit int, so it should also be a String.
   - Full card number will be masked in logs.
   - This class could have spring boot validation annotations to validate the input data, such as @NotNull, @Size, @Pattern etc. However, I decided to implement validation logic in the service layer, because it allows for more complex validation rules that cannot be easily expressed with annotations. For example, cross-field validation (e.g. expiry date should be in the future).
   - All the fields should be nullable, because if any of them is missing, the validation logic will catch that and return appropriate error messages. This also allows for more flexible error reporting, as we can return multiple validation errors in the response.
   - cvv might have leading zeros, so it should be a String instead of an int.


2. PostPaymentResponse class
   - It looks like toString() method was copied from GetPaymentResponse, I'll update it it to reflect the fields in this class.
   - I assume that only this class needs to be stored in the database, since it contains all the information about the payment (including the status). GetPaymentResponse is 1 to 1 view of PostPaymentResponse.
   - Added @JsonProperties for snake_case formatting of JSON properties in the request and response, to follow PostPaymentRequest class.


3. GetPaymentResponse class
   - This class has the same fields as PostPaymentResponse. It doesn't carry any extra information and seems to be redundant. However, I decided to keep it in my solution because it represents a different concept in the API.
   - Added @JsonProperties for snake_case formatting of JSON properties in the request and response, to follow PostPaymentRequest class.


4. PaymentStatus enum
   - Rejected status is not needed since incorrect details will result in ValidationException, and ErrorResponse will be returned with appropriate error messages. 
   - Requirements for PostPaymentResponse and GetPaymentResponse classes restrict status value only to Authorized and Declined. I assume that there's no need to store Rejected events in the database. Records with such status would never be returned in the response.


5. ErrorResponse class
   - I modified the class to have an option to store a list of error messages to allow for multiple validation errors to be returned in the response. This is more flexible and allows for better error reporting.
   - Single field message is still supported and will be used for bank and service side errors, while the list of messages will be used for validation errors. This allows for better separation of concerns and more informative error responses.


6. Exception handling
   - I added PaymentNotFoundException to handle the case when a payment with the given ID is not found in the database instead of EventProcessingException. This will return a 404 Not Found response with an appropriate error message.
   - I added PaymentValidationException to handle the case when the input data is invalid. This will return a 400 Bad Request response with a list of validation error messages.
   - I added new exception handlers in CommonExceptionHandler for PaymentNotFoundException and PaymentValidationException as well as generic Exception(500) to return appropriate HTTP status codes and error messages in the response.
   - I added an exception handler for MethodArgumentTypeMismatchException to handle cases where the path variable ID is not a valid UUID. This will return a 400 Bad Request response with an appropriate error message.
   - I added an exception handler for HttpMessageNotReadableException to handle cases where the request body is not valid JSON or is missing required fields. This will return a 400 Bad Request response with an appropriate error message.
   - EventProcessingException is now used only for Bank side errors, such as timeouts, connection issues etc. This will return a 502 Bad Gateway response with an appropriate error message.


7. PaymentGatewayController class
    - I updated @RequestMapping to "/api/v1/payments" to follow RESTful conventions. (Not necessary).
    - I added POST /api/v1/payments endpoint to create a new payment. This endpoint will return a ResponseEntity with a 201 Created status code and the created payment in the response body.
    - I added annotations for swagger documentation, such as @Operation, @ApiResponse etc. to provide better API documentation and make it easier for clients to understand how to use the API.


8. Swagger configuration
   - I added OpenAPI configuration to customize the API documentation, such as setting the title, description, version etc.


9. BankClient class
   - I added a BankClient class to handle communication with the bank simulator. This class will use RestTemplate to send requests to the bank simulator and receive responses. This will allow for better separation of concerns and make the code more modular and easier to maintain.
   - I added error handling in the BankClient class to catch any exceptions that may occur during communication with the bank simulator, such as timeouts, connection issues etc. This will allow for better error reporting and handling of bank side errors. EventProcessingException will be thrown in case of any bank side errors, which will result in a 502 Bad Gateway response.
   - Response 400 should not occur during communication with the bank simulator, because all the validation should be done in the service layer before sending the request to the bank. If the request is invalid, a PaymentValidationException will be thrown and a 400 Bad Request response will be returned without even contacting the bank simulator. However, if we do receive a 400 response from the bank simulator, it will be treated as a bank side error and an EventProcessingException will be thrown, resulting in a 502 Bad Gateway response.


10. PaymentsRepository class
    - I decided to keep this class as it is. But usually repositories store entities, not DTOs. In this case, PostPaymentResponse is being stored in the database, which is a bit unusual, but it works for this simple application.
    - In this case we already have 2 DTOs that represent the same concept (PostPaymentResponse and GetPaymentResponse), so it would be redundant to create a separate entity class.
    - In a more complex application, I would create a separate Payment entity class to represent the payment in the database, and then map it to PostPaymentResponse and GetPaymentResponse DTOs for the API layer.


11. PaymentGatewayService class
    - I added validation logic in the service layer to validate the input data before processing the payment. This includes checking for null values, validating the format of the card number, expiry date, cvv etc. If any validation fails, a PaymentValidationException will be thrown with appropriate error messages.
    - I added logic to process the payment by sending a request to the bank simulator using the BankClient class. The response from the bank simulator will determine the status of the payment (Authorized or Declined).
    - I added logic to save the payment details in the database using the PaymentsRepository class. Only successful payment responses (Authorized and Declined) will be saved in the database, while Rejected payments will not be saved, since they will never reach the bank.
    - This class also contains a simple mapper method that represents the mapping between PostPaymentResponse and GetPaymentResponse (redundant and kept only for demonstration purposes).
    - In a more complex application, I would probably use a library like MapStruct or ModelMapper for this purpose, but for this simple application, a manual mapping method is sufficient.


12. Testing
    - I added unit tests with Mockito for BankClient, PaymentGatewayService and PaymentGatewayController classes to ensure that the business logic is working correctly and to achieve good test coverage. 
    - These tests cover various scenarios, including successful payments, declined payments, validation errors, payment not found etc.
    - I added integration tests for the PaymentGatewayController class to test the API endpoints and ensure that they are working correctly with the service layer and the bank simulator. These tests use WireMock to send requests to the API endpoints and verify the responses.