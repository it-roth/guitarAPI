package com.example.guitarapi.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.guitarapi.utils.ResponseUtil;
import com.example.guitarapi.models.ApiResponse;

import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @GetMapping("/mappings")
    public ApiResponse mappings() {
        var map = handlerMapping.getHandlerMethods().entrySet().stream()
                .map(e -> e.getKey().toString())
                .sorted()
                .collect(Collectors.toList());
        return ResponseUtil.success(map);
    }
}
