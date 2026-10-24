package com.example.ragkit.utils;

import java.util.List;

/**
 * 余弦相似度。
 *
 * <p>边界说明
 * <ul>
 *     <li>入参为null，直接抛出异常</li>
 *     <li>两个向量维度不一致：抛异常，防止计算错误</li>
 *     <li>存在零向量，无意义抛异常</li>
 * </ul>
 * <p>返回值域[-1,1]
 */
public class CosineSimilarity {

    private CosineSimilarity() {
    }

    /**
     * 计算两个向量的余弦相似度，返回值域 [-1, 1]。
     *
     * @throws IllegalArgumentException 维度不一致 / 零向量 / 入参为 null 或空
     * 允许某一向量元素为null，不对其进行处理
     */
    public static double cosine(List<Double> a, List<Double> b) {
        if(a == null || b == null){
            throw new IllegalArgumentException("向量不能为null");
        }
        if(a.isEmpty() || b.isEmpty()){
            throw new IllegalArgumentException("存在空列表");
        }
        if(a.size() != b.size()){
            throw new IllegalArgumentException("向量维度不一致");
        }


        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);

            dotProduct += x * y;
            normA += x*x;
            normB += y*y;
        }

        if(normA == 0.0 || normB == 0.0){
            throw new IllegalArgumentException("存在零向量，无法计算");
        }

        return dotProduct /(Math.sqrt(normA)*Math.sqrt(normB));
    }
}
