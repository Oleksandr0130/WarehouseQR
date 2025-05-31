package com.warehouse.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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
public class FrontendController {

    @GetMapping(value = { "/", "/{x:[\\w\\-]+}", "/{x:^(?!api$).*$}/**" })
    public String index() {
        return "forward:/index.html";
    }
}
