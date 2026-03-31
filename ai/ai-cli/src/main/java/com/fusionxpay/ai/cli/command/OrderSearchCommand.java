package com.fusionxpay.ai.cli.command;

import com.fusionxpay.ai.cli.command.support.AbstractCliLeafCommand;
import com.fusionxpay.ai.cli.command.support.CliRenderSupport;
import com.fusionxpay.ai.cli.service.CliCommandService;
import com.fusionxpay.ai.common.dto.order.OrderPageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Command(name = "search", description = "Search orders for the current merchant")
public class OrderSearchCommand extends AbstractCliLeafCommand implements Callable<Integer> {

    private final CliCommandService commandService;

    @Option(names = "--status", description = "Optional order status")
    String status;

    @Option(names = "--order-number", description = "Optional order number filter")
    String orderNumber;

    @Option(names = "--from", description = "Optional start date in YYYY-MM-DD format")
    String from;

    @Option(names = "--to", description = "Optional end date in YYYY-MM-DD format")
    String to;

    @Option(names = "--page", defaultValue = "0", description = "Zero-based page number")
    int page;

    @Option(names = "--size", defaultValue = "20", description = "Page size")
    int size;

    @Override
    public Integer call() {
        OrderPageResult pageResult = commandService.searchOrders(status, orderNumber, from, to, page, size);
        setAuditOutputSummary(CliRenderSupport.renderOrderPage(out(), pageResult));
        return 0;
    }

    @Override
    public String auditActionName() {
        return "order.search";
    }

    @Override
    public String auditInputSummary() {
        return "status=" + value(status) + ", orderNumber=" + value(orderNumber)
                + ", from=" + value(from) + ", to=" + value(to)
                + ", page=" + page + ", size=" + size;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
