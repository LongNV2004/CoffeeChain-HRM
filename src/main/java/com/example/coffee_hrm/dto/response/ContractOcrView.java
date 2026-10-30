package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ContractOcrView {

    private final String status;
    private final String message;
    private final String fileName;
    private final long fileSize;
    private final List<Field> fields;
    private final Integer suggestedEmployeeId;
    private final List<String> warnings;
    private final Signature managerSignature;
    private final Signature employeeSignature;
    private final String overall;
    private final boolean allowsCreate;

    @Getter
    @Builder
    public static class Field {
        private final String key;
        private final String label;
        private final String value;
        private final String display;
        private final boolean recognized;
        private final String source;
    }

    @Getter
    @Builder
    public static class Signature {
        private final String party;
        private final String status;
        private final String label;
        private final Integer page;
        private final String location;
        private final String reason;
    }
}
