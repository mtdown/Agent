package com.et.cloud.rag;

import lombok.Data;

@Data
public class RagQueryExpansion {

    private String rewrittenQuestion;

    private String hypotheticalAnswer;

    public RagQueryExpansion() {
    }

    public RagQueryExpansion(String rewrittenQuestion, String hypotheticalAnswer) {
        this.rewrittenQuestion = rewrittenQuestion;
        this.hypotheticalAnswer = hypotheticalAnswer;
    }
}
