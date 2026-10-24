package com.example.ragkit.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CosineSimilarityTest {

    private static final double DELTA = 1e-10;

    @Test
    @DisplayName("完全相同的向量：相似度为 1")
    void cosine_sameVector_returnsOne() {
        List<Double> v = List.of(1.0,2.0,3.0);
        double result = CosineSimilarity.cosine(v,v);
        assertEquals(1,result,DELTA);
    }

    @Test
    @DisplayName("正交向量：相似度为 0")
    void cosine_orthogonalVectors_returnsZero() {
        List<Double> v = List.of(1.0,0.0);
        List<Double> u =  List.of(0.0,2.0);
        double result = CosineSimilarity.cosine(v,u);
        assertEquals(0,result,DELTA);
    }

    @Test
    @DisplayName("完全反向的向量：相似度为 -1")
    void cosine_oppositeVectors_returnsMinusOne() {
        List<Double> v = List.of(1.0,2.0,3.0);
        List<Double> u =  List.of(-1.0,-2.0,-3.0);
        double result = CosineSimilarity.cosine(v,u);
        assertEquals(-1,result,DELTA);
    }

    @Test
    @DisplayName("手算可验证的例子：[1,0] vs [3,4]")
    void cosine_handCalculatedExample() {
        List<Double> v = List.of(1.0,0.0);
        List<Double> u =  List.of(3.0,4.0);
        double result = CosineSimilarity.cosine(v,u);
        assertEquals(3.0/5.0,result,DELTA);
    }

    @Test
    @DisplayName("维度不一致：抛 IllegalArgumentException")
    void cosine_dimensionMismatch_throws() {
        assertThrows(IllegalArgumentException.class, () -> {
            List<Double> v = List.of(1.0,2.0,3.0);
            List<Double> u =  List.of(-1.0,-2.0);
            double result = CosineSimilarity.cosine(v,u);
        });
    }

    @Test
    @DisplayName("零向量：抛 IllegalArgumentException")
    void cosine_zeroVector_throws() {
        assertThrows(IllegalArgumentException.class, () -> {
            List<Double> v = List.of(0.0,0.0);
            List<Double> u =  List.of(0.0,1.0);
            double result = CosineSimilarity.cosine(v,u);
        });
    }

    @Test
    @DisplayName("null 入参：抛 IllegalArgumentException")
    void cosine_nullLeftInput_throws() {
        assertThrows(IllegalArgumentException.class, () -> {
            List<Double> u =  List.of(1.0,2.0);
            double result = CosineSimilarity.cosine(null,u);
        });
    }

    @Test
    @DisplayName("null 入参：抛 IllegalArgumentException")
    void cosine_nullRightInput_throws() {
        assertThrows(IllegalArgumentException.class, () -> {
            List<Double> u =  List.of(1.0,2.0);
            double result = CosineSimilarity.cosine(u,null);
        });
    }
}