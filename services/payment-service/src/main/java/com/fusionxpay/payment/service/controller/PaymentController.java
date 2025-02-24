package com.fusionxpay.payment.service.controller; 

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/create-payment") 
public class PaymentController {

    @GetMapping
    public String paymentEndpoint() {
        return "Hello from Payment Service!!";
    }
}