package com.example.coffee_hrm.dto.response;

public record ContractDownload(String fileName, String contentType, byte[] content) {
}
