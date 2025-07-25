// jsp
package com.example.NewsHubCommunity.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SelfIntroController {

    @GetMapping("/selfIntro")
    public String selfIntro() {
        // 返回视图逻辑名，对应 /WEB-INF/jsp/NewFile.jsp
        return "NewFile";
    }
    @GetMapping("/testjsp")
    public String testjsp() {
        // 返回视图逻辑名，对应 /WEB-INF/jsp/home.jsp
        return "home";
    }
}