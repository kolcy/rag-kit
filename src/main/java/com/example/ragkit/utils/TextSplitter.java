package com.example.ragkit.utils;

import java.util.ArrayList;
import java.util.List;

public class TextSplitter {
//    chunkSize是切分窗口大小，overlap是重叠部分长度
    private static final int DEFAULT_CHUNK_SIZE = 400;
    private static final int DEFAULT_OVERLAP = 50;

    private final int chunkSize;
    private final int overlap;

    public TextSplitter() {
        this(DEFAULT_CHUNK_SIZE,DEFAULT_OVERLAP);
    }

    public TextSplitter(int chunkSize,int overlap) {
        if(chunkSize <= 0){
            throw new IllegalArgumentException("切分长度不能小于等于0");
        }
        if(overlap < 0){
            throw new IllegalArgumentException("重叠部分长度不能小于0");
        }
        if(chunkSize <=overlap){
            throw new IllegalArgumentException("重叠部分长度不能大于等于切分长度");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

        public List<String> split(String text){
            List<String> chunk = new ArrayList<>();
            int start = 0;
            while(start<text.length()){
                int end = Math.min(start+chunkSize,text.length());
                chunk.add(text.substring(start, end));
                if(end==text.length()){
                    break;
                }
                //保证至少取一个字符防止死循环
                start = Math.max(end-overlap,start+1);
            }
            return chunk;
    }

}
