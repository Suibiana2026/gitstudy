package com.bigData.main.service.MySQL;

import com.bigData.main.Repository.PlatformProportionRepository;
import com.bigData.main.pojo.PlatformProportion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlatformProportionService {
    @Autowired
    private PlatformProportionRepository repository;

    public List<PlatformProportion> getAllProportions() {
        return repository.findAll();
    }
}
