package com.fusionxpay.ai.cli.command;

import com.fusionxpay.ai.cli.command.support.AbstractCliLeafCommand;
import com.fusionxpay.ai.cli.command.support.CliRenderSupport;
import com.fusionxpay.ai.cli.service.CliCommandService;
import com.fusionxpay.ai.common.dto.order.OrderRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Command(name = "get", description = "Get an order by order ID or order number")
public class OrderGetCommand extends AbstractCliLeafCommand implements Callable<Integer> {

    private final CliCommandService commandService;

    @Option(names = "--order-id", description = "Order UUID")
    String orderId;

    @Option(names = "--order-number", description = "Merchant order number")
    String orderNumber;

    @Override
    public Integer call() {
        OrderRecord order = commandService.getOrder(orderId, orderNumber);
        setAuditOutputSummary(CliRenderSupport.renderOrder(out(), order));
        return 0;
    }

    @Override
    public String auditActionName() {
        return "order.get";
    }

    @Override
    public String auditInputSummary() {
        return "orderId=" + value(orderId) + ", orderNumber=" + value(orderNumber);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
