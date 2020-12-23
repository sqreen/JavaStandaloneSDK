package io.sqreen.sasdk.signals_dto.context.http;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class Request {

    public Long startProcessingTime;
    public Long endProcessingTime;
    public String rid;
    public Map<String, String> headers; // XXX: not right. Should allow several values
    public String userAgent;
    public String scheme = "http";
    public HttpMethod verb;
    public String host;
    public Integer port;
    public String remoteIp;
    public Integer remotePort;
    public String path;
    public String referer;
    public Parameters parameters;
}
