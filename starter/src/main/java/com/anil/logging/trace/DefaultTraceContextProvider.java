package com.anil.logging.trace;

import com.anil.logging.context.MdcKeys;
import org.slf4j.MDC;

public class DefaultTraceContextProvider implements TraceContextProvider {
    @Override
    public TraceContext currentTraceContext() {
        return new TraceContext(MDC.get(MdcKeys.TRACE_ID), MDC.get(MdcKeys.SPAN_ID));
    }
}
