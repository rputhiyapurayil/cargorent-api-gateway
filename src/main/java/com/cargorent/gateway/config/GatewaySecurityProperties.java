package com.cargorent.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Route rules are written as "METHOD /path/pattern" (METHOD may be * for any).
 *
 * @param jwtSecret     shared HS256 secret, identical to user-service
 * @param publicRoutes  routes that work without a token (a token, if sent, is still validated)
 * @param internalRoutes service-to-service routes that are never reachable from outside
 */
@ConfigurationProperties(prefix = "cargorent.security")
public record GatewaySecurityProperties(String jwtSecret, List<String> publicRoutes, List<String> internalRoutes) {
}
