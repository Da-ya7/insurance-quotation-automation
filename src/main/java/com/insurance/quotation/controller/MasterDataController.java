package com.insurance.quotation.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.insurance.quotation.entity.AuditLog;
import com.insurance.quotation.entity.RequiredField;
import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.repository.AuditLogRepository;
import com.insurance.quotation.repository.RequiredFieldRepository;
import com.insurance.quotation.service.master.CountryDef;
import com.insurance.quotation.service.master.IdentityTypeDef;
import com.insurance.quotation.service.master.InsuranceTypeDef;
import com.insurance.quotation.service.master.KeywordDef;
import com.insurance.quotation.service.master.MasterDataService;
import com.insurance.quotation.service.validation.ValidationService;

/**
 * Master data screens. Any staff user can read; only ADMIN can change
 * (enforced in SecurityConfig). Changes are written to the audit log.
 */
@RestController
@RequestMapping("/api/master")
public class MasterDataController {

    private static final Logger log = LoggerFactory.getLogger(MasterDataController.class);

    public record RequiredFieldDto(
            Long id, String fieldName, String displayLabel, String dataType, String policyType,
            Boolean isActive, Boolean isMandatory, String validationRegex,
            Integer sortOrder, String helpText) {

        static RequiredFieldDto of(RequiredField f) {
            return new RequiredFieldDto(f.getId(), f.getFieldName(), f.getDisplayLabel(),
                    f.getDataType().name(), f.getPolicyType(), f.getIsActive(), f.getIsMandatory(),
                    f.getValidationRegex(), f.getSortOrder(), f.getHelpText());
        }
    }

    public record KeywordRequest(String keyword, Boolean active) {
    }

    private final MasterDataService master;
    private final RequiredFieldRepository requiredFieldRepository;
    private final AuditLogRepository auditLogRepository;

    public MasterDataController(
            MasterDataService master, RequiredFieldRepository requiredFieldRepository,
            AuditLogRepository auditLogRepository) {
        this.master = master;
        this.requiredFieldRepository = requiredFieldRepository;
        this.auditLogRepository = auditLogRepository;
    }

    // ---------------- insurance types ----------------

    @GetMapping("/insurance-types")
    public List<InsuranceTypeDef> insuranceTypes() {
        return master.insuranceTypes(false);
    }

    @PostMapping("/insurance-types")
    public InsuranceTypeDef createInsuranceType(@RequestBody InsuranceTypeDef body, Authentication auth) {
        master.saveInsuranceType(body);
        audit("INSURANCE_TYPE_SAVED", body.code(), auth);
        return master.insuranceType(body.code()).orElseThrow();
    }

    @PutMapping("/insurance-types/{code}")
    public InsuranceTypeDef updateInsuranceType(
            @PathVariable String code, @RequestBody InsuranceTypeDef body, Authentication auth) {
        master.insuranceType(code).orElseThrow(() -> new IllegalArgumentException("Unknown insurance type."));
        master.saveInsuranceType(new InsuranceTypeDef(code, body.label(), body.keywords(),
                body.basePremium() == null ? BigDecimal.ZERO : body.basePremium(),
                body.ratePercent() == null ? BigDecimal.ZERO : body.ratePercent(),
                body.rateBasisField(), body.active(), body.sortOrder()));
        audit("INSURANCE_TYPE_SAVED", code, auth);
        return master.insuranceType(code).orElseThrow();
    }

    // ---------------- countries ----------------

    @GetMapping("/countries")
    public List<CountryDef> countries() {
        return master.countries(false);
    }

    @PutMapping("/countries/{code}")
    public CountryDef updateCountry(@PathVariable String code, @RequestBody CountryDef body, Authentication auth) {
        master.saveCountry(new CountryDef(code, body.name(), body.active()));
        audit("COUNTRY_SAVED", code, auth);
        return master.countries(false).stream()
                .filter(c -> c.code().equalsIgnoreCase(code)).findFirst().orElseThrow();
    }

    // ---------------- identity documents ----------------

    @GetMapping("/identity-types")
    public List<IdentityTypeDef> identityTypes(@RequestParam(required = false) String country) {
        if (country == null || country.isBlank()) {
            return master.allIdentityTypes();
        }
        String cc = master.resolveCountryCode(country);
        String key = cc == null ? country.trim() : cc;
        return master.allIdentityTypes().stream()
                .filter(t -> t.countryCode().equalsIgnoreCase(key)).toList();
    }

    @PostMapping("/identity-types")
    public IdentityTypeDef createIdentityType(@RequestBody IdentityTypeDef body, Authentication auth) {
        IdentityTypeDef saved = master.saveIdentityType(new IdentityTypeDef(
                null, body.countryCode(), body.code(), body.label(),
                body.validationRegex(), body.example(), body.active()));
        audit("IDENTITY_TYPE_SAVED", saved.countryCode() + "/" + saved.code(), auth);
        return saved;
    }

    @PutMapping("/identity-types/{id}")
    public IdentityTypeDef updateIdentityType(
            @PathVariable Long id, @RequestBody IdentityTypeDef body, Authentication auth) {
        IdentityTypeDef saved = master.saveIdentityType(new IdentityTypeDef(
                id, body.countryCode(), body.code(), body.label(),
                body.validationRegex(), body.example(), body.active()));
        audit("IDENTITY_TYPE_SAVED", saved.countryCode() + "/" + saved.code(), auth);
        return saved;
    }

    // ---------------- required fields ----------------

    @GetMapping("/required-fields")
    @Transactional(readOnly = true)
    public List<RequiredFieldDto> requiredFields(@RequestParam(required = false) String insuranceType) {
        return requiredFieldRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(f -> insuranceType == null || insuranceType.isBlank()
                        || ValidationService.appliesTo(f, insuranceType))
                .map(RequiredFieldDto::of).toList();
    }

    @PostMapping("/required-fields")
    @Transactional
    public ResponseEntity<?> createRequiredField(@RequestBody RequiredFieldDto body, Authentication auth) {
        if (body.fieldName() == null || !body.fieldName().matches("^[A-Za-z][A-Za-z0-9]{1,79}$")) {
            throw new IllegalArgumentException("Field name must be letters/digits only, e.g. vehicleColour.");
        }
        if (requiredFieldRepository.existsByFieldNameIgnoreCase(body.fieldName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("A field with this name already exists.");
        }
        RequiredField f = new RequiredField();
        f.setFieldName(body.fieldName());
        apply(f, body);
        f = requiredFieldRepository.save(f);
        audit("REQUIRED_FIELD_CREATED", f.getFieldName(), auth);
        return ResponseEntity.ok(RequiredFieldDto.of(f));
    }

    @PutMapping("/required-fields/{id}")
    @Transactional
    public ResponseEntity<?> updateRequiredField(
            @PathVariable Long id, @RequestBody RequiredFieldDto body, Authentication auth) {
        RequiredField f = requiredFieldRepository.findById(id).orElse(null);
        if (f == null) {
            return ResponseEntity.notFound().build();
        }
        apply(f, body);
        f = requiredFieldRepository.save(f);
        audit("REQUIRED_FIELD_CHANGED", f.getFieldName()
                + " mandatory=" + f.getIsMandatory() + " active=" + f.getIsActive(), auth);
        return ResponseEntity.ok(RequiredFieldDto.of(f));
    }

    private void apply(RequiredField f, RequiredFieldDto b) {
        if (b.displayLabel() != null && !b.displayLabel().isBlank()) {
            f.setDisplayLabel(b.displayLabel().trim());
        } else if (f.getDisplayLabel() == null) {
            throw new IllegalArgumentException("Display label is required.");
        }
        if (b.dataType() != null) {
            try {
                f.setDataType(FieldDataType.valueOf(b.dataType().trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Data type must be TEXT, DATE, NUMBER, EMAIL or PHONE.");
            }
        } else if (f.getDataType() == null) {
            f.setDataType(FieldDataType.TEXT);
        }
        f.setPolicyType(b.policyType() == null || b.policyType().isBlank()
                ? null : b.policyType().trim().toUpperCase(Locale.ROOT));
        if (b.isActive() != null) {
            f.setIsActive(b.isActive());
        }
        if (b.isMandatory() != null) {
            f.setIsMandatory(b.isMandatory());
        }
        MasterDataService.checkRegex(b.validationRegex());
        f.setValidationRegex(b.validationRegex() == null || b.validationRegex().isBlank()
                ? null : b.validationRegex());
        if (b.sortOrder() != null) {
            f.setSortOrder(b.sortOrder());
        }
        f.setHelpText(b.helpText() == null || b.helpText().isBlank() ? null : b.helpText().trim());
    }

    // ---------------- email subject keywords ----------------

    @GetMapping("/email-keywords")
    public List<KeywordDef> keywords() {
        return master.keywords();
    }

    @PostMapping("/email-keywords")
    public List<KeywordDef> addKeyword(@RequestBody KeywordRequest body, Authentication auth) {
        master.addKeyword(body.keyword());
        audit("KEYWORD_ADDED", body.keyword(), auth);
        return master.keywords();
    }

    @PutMapping("/email-keywords/{id}")
    public List<KeywordDef> setKeyword(
            @PathVariable Long id, @RequestBody KeywordRequest body, Authentication auth) {
        master.setKeywordActive(id, body.active() == null || body.active());
        audit("KEYWORD_CHANGED", String.valueOf(id), auth);
        return master.keywords();
    }

    @DeleteMapping("/email-keywords/{id}")
    public List<KeywordDef> deleteKeyword(@PathVariable Long id, Authentication auth) {
        master.deleteKeyword(id);
        audit("KEYWORD_DELETED", String.valueOf(id), auth);
        return master.keywords();
    }

    // ---------------- audit ----------------

    private void audit(String action, String detail, Authentication auth) {
        try {
            AuditLog a = new AuditLog();
            a.setEntityType("MASTER_DATA");
            a.setEntityId(0L);
            a.setAction(action);
            String who = auth == null ? "unknown" : auth.getName();
            a.setPerformedBy(who.length() > 60 ? who.substring(0, 60) : who);
            a.setDetails(detail);
            a.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(a);
        } catch (RuntimeException e) {
            log.warn("Could not write audit log: {}", e.getMessage());
        }
    }
}
