package com.example.api_gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class GatewayRouteAuditFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouteAuditFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            URI routedUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
            Object routeObj = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
            String routeId = routeObj != null ? routeObj.toString() : "unknown-route";

            if (routedUri != null) {
                String target = routedUri.getHost() + ":" + routedUri.getPort();
                exchange.getResponse().getHeaders().set("X-Gateway-Routed-Host", target);

                log.info("[ROUTING] method={} path={} route={} target={}",
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI().getPath(),
                        routeId,
                        routedUri);
            }
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
