package com.spotlink.operator;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperatorController {

    private final OperatorService operatorService;

    public OperatorController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    @GetMapping({"/operator/me", "/v1/operator/me"})
    OperatorDtos.OperatorAccountDto me() {
        return operatorService.me();
    }

    @GetMapping({"/operator/dashboard/summary", "/v1/operator/dashboard/summary"})
    OperatorDtos.OperatorDashboardSummary summary() {
        return operatorService.summary();
    }

    @GetMapping({"/operator/resources/health", "/v1/operator/resources/health"})
    List<OperatorDtos.OperatorResourceHealth> resourceHealth() {
        return operatorService.resourceHealth();
    }
}
