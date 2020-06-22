package io.sqreen.sasdk.signals_dto.context.http;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class Response {

    public Integer status;
    public Integer contentLength;
    public String contentType;
    public Map<String, String> headers;
}
