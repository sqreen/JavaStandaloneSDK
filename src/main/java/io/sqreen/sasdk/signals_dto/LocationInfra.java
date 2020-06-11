package io.sqreen.sasdk.signals_dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class LocationInfra {
    public Infra infra;

    public static class Infra {
        public String agentType;
        public String agentVersion;
        public String osType;
        public String hostname;
        public String runtimeType;
        public String runtimeVersion;
        public String libsqreenVersion;
    }
}
