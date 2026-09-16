package com.bigData.main.controller.MySQL;

import com.bigData.main.pojo.PlatformProportion;
import com.bigData.main.service.MySQL.PlatformProportionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform-proportion")
@CrossOrigin
public class PlatformProportionController {
    @Autowired
    private PlatformProportionService service;

    @GetMapping
    public List<PlatformProportion> getAllProportions() {
        return service.getAllProportions();
    }
}
