package com.example.ragkit.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class VectorJson {
    private VectorJson(){}

    public static String toJson(ObjectMapper om, List<Double> v){
        try {
            return om.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("向量序列化失败", e);
        }
    }

    public static List<Double> fromJson(ObjectMapper om, String json) {
        try {
            return om.readValue(json, new TypeReference<List<Double>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("向量反序列化失败", e);
        }
    }

}
