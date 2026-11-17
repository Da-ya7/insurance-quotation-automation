// package com.insurance.quotation.service.master;

// public record CountryDef(String code, String name, boolean active) {
// }




package com.insurance.quotation.service.master;

public record CountryDef(
        String code,
        String name,
        boolean active,
        String callingCode
) {
    // Backward compatibility for existing 3-argument constructor calls.
    public CountryDef(String code, String name, boolean active) {
        this(code, name, active, null);
    }
}
