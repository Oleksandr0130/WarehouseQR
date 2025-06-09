package com.warehouse.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

//@Controller
//public class FrontendController {
//
//    @GetMapping("/{path:[^\\.]*}")
//    public String redirect() {
//        // Отдает index.html для любого маршрута, который не содержит точку (например, /about)
//        return "forward:/index.html";
//    }
//}
@Controller
@RequestMapping("/")
public class FrontendController {

    @RequestMapping(value = { "/", "/{path:^((?!\\.).)*$}" })
    public String index() {
        return "forward:/index.html";
    }

}
