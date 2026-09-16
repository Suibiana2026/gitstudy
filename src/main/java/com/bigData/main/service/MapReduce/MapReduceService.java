package com.bigData.main.service.MapReduce;

import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface MapReduceService {
    Map<String, Map<String, Integer>> wordFrequencyStatistics(String filePaths, String field);
    String duplicationEliminating(String filePaths, String dedupFields);
    String saveProcessedFile(MultipartFile file, String operationType, String field);
}