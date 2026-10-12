package com.insurance.quotation.service.extraction;

import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.FieldSource;

public class ExtractedFieldData {

    private String fieldName;
    private String fieldValue;
    private FieldDataType dataType;
    private FieldSource source;
    private Double confidence;
    private boolean missing;

    public ExtractedFieldData() {
    }

    public ExtractedFieldData(
            String fieldName,
            String fieldValue,
            FieldDataType dataType,
            FieldSource source,
            Double confidence,
            boolean missing) {

        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        this.dataType = dataType;
        this.source = source;
        this.confidence = confidence;
        this.missing = missing;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    public FieldDataType getDataType() {
        return dataType;
    }

    public void setDataType(FieldDataType dataType) {
        this.dataType = dataType;
    }

    public FieldSource getSource() {
        return source;
    }

    public void setSource(FieldSource source) {
        this.source = source;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public boolean isMissing() {
        return missing;
    }

    public void setMissing(boolean missing) {
        this.missing = missing;
    }

    @Override
    public String toString() {
        return "ExtractedFieldData{" +
                "fieldName='" + fieldName + '\'' +
                ", fieldValue='" + fieldValue + '\'' +
                ", dataType=" + dataType +
                ", source=" + source +
                ", confidence=" + confidence +
                ", missing=" + missing +
                '}';
    }
}