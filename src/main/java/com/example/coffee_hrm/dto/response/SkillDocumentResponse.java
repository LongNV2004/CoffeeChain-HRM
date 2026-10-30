package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SkillDocumentResponse {

    private final Integer id;
    private final String originalName;
    private final long sizeBytes;
}
