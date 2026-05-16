package com.mgh.backend.cashier.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
@Controller
public class ForwardController {

    @RequestMapping(value = {"/pos", "/pos/**"})
    public String forward() {
        return "forward:/pos/index.html";
    }
}