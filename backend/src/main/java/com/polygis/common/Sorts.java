package com.polygis.common;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Sort allowlist for list endpoints: clients may only order by columns that are meant to be visible
 * ({@code ?sort=passwordHash} would otherwise turn ordering into an oracle, and unknown fields into 500s).
 */
public final class Sorts {

    private Sorts() {
    }

    public static Pageable allow(Pageable pageable, String... properties) {
        Set<String> allowed = Set.of(properties);
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw ApiException.badRequest("invalid_sort",
                        "Cannot sort by '" + order.getProperty() + "'. Allowed: " + new java.util.TreeSet<>(allowed));
            }
        }
        return pageable;
    }
}
