package com.polygis.tenancy;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.polygis.common.ApiException;

/** Quotas and feature flags of the current tenant's plan. Hook for billing: set tenants.plan from your PSP webhook. */
@Service
public class PlanService {

    private final JdbcTemplate jdbc;
    private final PlanProperties props;

    public PlanService(JdbcTemplate jdbc, PlanProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public String currentPlanName() {
        return jdbc.queryForObject("select plan from public.tenants where id = ?", String.class,
                TenantContext.require().id());
    }

    public PlanProperties.Plan currentPlan() {
        return props.plans().getOrDefault(currentPlanName(), PlanProperties.Plan.UNLIMITED);
    }

    /** @throws ApiException 402 {@code quota_exceeded} when the plan's user limit is reached */
    public void assertCanAddUser(long currentUsers) {
        int max = currentPlan().maxUsers();
        if (max >= 0 && currentUsers >= max) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "quota_exceeded",
                    "Your plan allows " + max + " users. Upgrade to add more.");
        }
    }

    public boolean hasFeature(String feature) {
        return currentPlan().features().contains(feature);
    }

    /** @throws ApiException 402 {@code feature_unavailable} when the plan does not include the feature */
    public void requireFeature(String feature) {
        if (!hasFeature(feature)) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "feature_unavailable",
                    "This feature is not included in your plan.");
        }
    }
}
