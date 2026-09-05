package com.anil.logging.context;

public final class MdcKeys {
    public static final String REQUEST_ID = "request_id";
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String USER_ID = "user_id";
    public static final String ROLES = "role_ids";
    public static final String AUTHENTICATED = "authenticated";
    public static final String SERVICE = "service";
    public static final String ENVIRONMENT = "environment";
    public static final String TYPE = "type";
    public static final String OPERATION = "operation";

    private MdcKeys() {
    }
}
