package com.anil.logging.masking;

import java.util.Map;

public interface SensitiveDataMasker {
    Object maskValue(String fieldName, Object value);

    String maskPayload(String payload);

    Map<String, ?> maskMap(Map<String, ?> source, MaskingTarget target);
}
