package com.example.chat.controller;

import com.example.chat.model.PublicClientConfigResponse;
import com.example.chat.service.ClientConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ClientConfigService clientConfigService;

    @GetMapping("/public")
    public PublicClientConfigResponse publicConfig() {
        return clientConfigService.getPublicConfig();
    }
}
