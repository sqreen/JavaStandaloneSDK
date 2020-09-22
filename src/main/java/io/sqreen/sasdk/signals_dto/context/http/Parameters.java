package io.sqreen.sasdk.signals_dto.context.http;

import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class Parameters {
    public String query;
    public String form;
    public String cookies;
    public String json;
    public String other;
}
