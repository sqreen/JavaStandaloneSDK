package io.sqreen.sasdk.signals_dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class Actor {

    public String[] ipAddresses;
    public String userAgent;
    public String[] identifiers;
    public String[] traits;
}
