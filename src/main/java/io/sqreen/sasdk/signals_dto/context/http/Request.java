package io.sqreen.sasdk.signals_dto.context.http;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class Request {

    public String rid;
    public Map<String, String> headers;
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
    
    public static class Parameters {
        public String query;
        public String form;
        public String cookies;
    }
}
