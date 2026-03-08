package com.example.EnterpriseRagCommunity.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class RootRedirectController {

    @GetMapping("/")
    public RedirectView root() {
        return new RedirectView("/portal/discover/home");
    }
}
