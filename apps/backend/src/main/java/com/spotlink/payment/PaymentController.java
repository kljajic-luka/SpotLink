package com.spotlink.payment;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping({"/payments/methods", "/v1/payments/methods"})
    List<PaymentDtos.PaymentMethodDto> methods() {
        return paymentService.methods();
    }

    @PostMapping({"/payments/intents", "/v1/payments/intents"})
    @ResponseStatus(HttpStatus.CREATED)
    PaymentDtos.PaymentIntentDto createIntent(@Valid @RequestBody PaymentDtos.CreatePaymentIntentRequest request) {
        return paymentService.createIntent(request);
    }

    @PostMapping({"/payments/intents/{paymentIntentId}/confirm", "/v1/payments/intents/{paymentIntentId}/confirm"})
    PaymentDtos.PaymentProviderResult confirm(@PathVariable UUID paymentIntentId) {
        return paymentService.confirm(paymentIntentId);
    }
}
