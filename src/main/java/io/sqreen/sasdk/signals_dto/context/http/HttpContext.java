package io.sqreen.sasdk.signals_dto.context.http;

import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class HttpContext {

    public Request request;

    public Response response;
}
