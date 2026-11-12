package com.polygis.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.polygis.config.AppProperties;
import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantInfo;

/**
 * Server-side rendered Thymeleaf pages living next to the REST API. nginx sends "/" to Nuxt, so these are
 * reachable on the backend port directly, or proxy e.g. /status and /oauth2/error explicitly.
 */
@Controller
public class ViewController {

    private final AppProperties props;

    public ViewController(AppProperties props) {
        this.props = props;
    }

    @GetMapping({"/", "/status"})
    public String status(Model model) {
        model.addAttribute("productName", props.mail().productName());
        model.addAttribute("tenant", TenantContext.current().map(TenantInfo::slug).orElse(null));
        return "status";
    }

    @GetMapping("/oauth2/error")
    public String oauth2Error(@RequestParam(defaultValue = "unknown") String reason, Model model) {
        model.addAttribute("productName", props.mail().productName());
        model.addAttribute("reason", reason);
        return "oauth2-error";
    }
}
