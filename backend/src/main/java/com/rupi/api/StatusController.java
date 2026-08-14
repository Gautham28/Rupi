package com.rupi.api;

import com.rupi.api.dto.StatusResponse;
import com.rupi.config.RupiProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StatusController {

    private final RupiProperties properties;

    public StatusController(RupiProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse("rupi", "ok", properties.demo().enabled());
    }
}
