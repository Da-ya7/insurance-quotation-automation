package com.insurance.quotation.service.client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.ClientVersion;
import com.insurance.quotation.repository.ClientRepository;
import com.insurance.quotation.repository.ClientVersionRepository;
import com.insurance.quotation.service.master.MasterDataService;

/**
 * The single place that creates, finds and changes client records.
 *
 * Rules:
 *  - A person is identified by their identity document first, then by
 *    email + name. A shared email address never merges two people.
 *  - Details that were empty are simply filled in.
 *  - Changing an existing SENSITIVE detail needs OTP proof (otpVerified) or a
 *    staff user. Without it the change is NOT applied and is reported back
 *    in ApplyResult.blocked so the caller can start the OTP flow.
 *  - Every applied change writes a new client_versions snapshot.
 */
@Service
public class ClientMasterService {

    /** Fields that can only be edited with OTP / staff authority. */
    public static final Set<String> SENSITIVE_FIELDS = Set.of(
            "name", "phone", "email", "dateOfBirth",
            "identityCountry", "identityType", "identityNumber");

    public record ClientDetails(
            String name,
            String email,
            String phone,
            LocalDate dateOfBirth,
            String gender,
            String nationality,
            String countryOfResidence,
            String identityCountry,
            String identityType,
            String identityNumber) {

        public boolean hasIdentity() {
            return identityType != null && !identityType.isBlank()
                    && identityNumber != null && !identityNumber.isBlank();
        }
    }

    public record ApplyResult(
            Client client,
            List<String> filled,
            List<String> changed,
            List<String> blocked) {

        public boolean hasBlockedChanges() {
            return !blocked.isEmpty();
        }
    }

    private final ClientRepository clientRepository;
    private final ClientVersionRepository versionRepository;
    private final IdentityProtectionService identity;
    private final MasterDataService master;   // may be null in unit tests

    @org.springframework.beans.factory.annotation.Autowired
    public ClientMasterService(
            ClientRepository clientRepository,
            ClientVersionRepository versionRepository,
            IdentityProtectionService identity,
            MasterDataService master) {
        this.clientRepository = clientRepository;
        this.versionRepository = versionRepository;
        this.identity = identity;
        this.master = master;
    }

    public ClientMasterService(
            ClientRepository clientRepository,
            ClientVersionRepository versionRepository,
            IdentityProtectionService identity) {
        this(clientRepository, versionRepository, identity, null);
    }

    /** "India" / "india" / "IN" -> "IN" so one person always hashes the same way. */
    private String country(ClientDetails d) {
        String c = d.identityCountry();
        if (c == null || c.isBlank()) {
            return null;
        }
        String code = master == null ? null : master.resolveCountryCode(c);
        return code != null ? code : c.trim().toUpperCase(Locale.ROOT);
    }

    private String type(ClientDetails d) {
        return d.identityType() == null || d.identityType().isBlank()
                ? null : MasterDataService.typeCode(d.identityType());
    }

    private String hashOf(ClientDetails d) {
        return identity.hash(country(d), type(d), d.identityNumber());
    }

    /** Find the person these details belong to, without creating anything. */
    @Transactional(readOnly = true)
    public Optional<Client> find(ClientDetails d) {

        if (d.hasIdentity()) {
            Optional<Client> byIdentity = clientRepository.findByIdentityNumberHash(hashOf(d));
            if (byIdentity.isPresent()) {
                return byIdentity;
            }
        }

        List<Client> sameEmail = clientRepository.findAllByEmailIgnoreCase(
                clean(d.email()));

        List<Client> matches = new ArrayList<>();
        for (Client c : sameEmail) {
            if (isSamePerson(c, d, sameEmail.size())) {
                matches.add(c);
            }
        }
        // Several equally good matches: do not guess between people.
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    /**
     * Find the client or create a new record, then apply the details.
     * Never overwrites an existing person with someone else's details.
     */
    @Transactional
    public ApplyResult findOrCreateAndApply(
            ClientDetails d, String source, String actor, boolean otpVerified) {

        Optional<Client> existing = find(d);

        if (existing.isPresent()) {
            return apply(existing.get(), d, source, actor, otpVerified);
        }

        Client c = new Client();
        c.setClientCode("TMP-" + UUID.randomUUID());
        c.setEmail(clean(d.email()));
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        c.setVersionNo(1);
        c.setName(blankToNull(d.name()));
        c.setPhone(blankToNull(d.phone()));
        c.setDateOfBirth(d.dateOfBirth());
        c.setGender(blankToNull(d.gender()));
        c.setNationality(blankToNull(d.nationality()));
        c.setCountryOfResidence(blankToNull(d.countryOfResidence()));
        setIdentity(c, d);

        c = clientRepository.save(c);
        c.setClientCode(String.format("CL-%06d", c.getId()));
        c = clientRepository.save(c);

        writeVersion(c, source, "created", actor, otpVerified);
        return new ApplyResult(c, List.of("created"), List.of(), List.of());
    }

    /** Apply submitted details to an existing client following the OTP rules. */
    @Transactional
    public ApplyResult apply(
            Client client, ClientDetails d, String source, String actor,
            boolean otpVerified) {

        List<String> filled = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<String> blocked = new ArrayList<>();

        // ---- simple text/date fields ----
        merge("name", client.getName(), blankToNull(d.name()), otpVerified,
                v -> client.setName(v), filled, changed, blocked);
        merge("phone", client.getPhone(), blankToNull(d.phone()), otpVerified,
                v -> client.setPhone(v), filled, changed, blocked);
        merge("email", client.getEmail(), blankToNull(clean(d.email())), otpVerified,
                v -> client.setEmail(v), filled, changed, blocked);
        merge("gender", client.getGender(), blankToNull(d.gender()), otpVerified,
                v -> client.setGender(v), filled, changed, blocked);
        merge("nationality", client.getNationality(), blankToNull(d.nationality()),
                otpVerified, v -> client.setNationality(v), filled, changed, blocked);
        merge("countryOfResidence", client.getCountryOfResidence(),
                blankToNull(d.countryOfResidence()), otpVerified,
                v -> client.setCountryOfResidence(v), filled, changed, blocked);

        if (d.dateOfBirth() != null) {
            LocalDate current = client.getDateOfBirth();
            if (current == null) {
                client.setDateOfBirth(d.dateOfBirth());
                filled.add("dateOfBirth");
            } else if (!current.equals(d.dateOfBirth())) {
                if (otpVerified) {
                    client.setDateOfBirth(d.dateOfBirth());
                    changed.add("dateOfBirth");
                } else {
                    blocked.add("dateOfBirth");
                }
            }
        }

        // ---- identity document ----
        if (d.hasIdentity()) {
            String newHash = hashOf(d);

            if (client.getIdentityNumberHash() == null) {
                // Make sure no OTHER person already owns this document.
                Optional<Client> owner = clientRepository.findByIdentityNumberHash(newHash);
                if (owner.isPresent() && !owner.get().getId().equals(client.getId())) {
                    blocked.add("identityNumber");
                } else {
                    setIdentity(client, d);
                    filled.add("identityNumber");
                }
            } else if (!newHash.equals(client.getIdentityNumberHash())) {
                Optional<Client> owner = clientRepository.findByIdentityNumberHash(newHash);
                if (owner.isPresent() && !owner.get().getId().equals(client.getId())) {
                    blocked.add("identityNumber");
                } else if (otpVerified) {
                    setIdentity(client, d);
                    changed.add("identityNumber");
                } else {
                    blocked.add("identityNumber");
                }
            }
        }

        if (!filled.isEmpty() || !changed.isEmpty()) {
            client.setVersionNo(client.getVersionNo() + 1);
            client.setUpdatedAt(LocalDateTime.now());
            clientRepository.save(client);

            List<String> all = new ArrayList<>(filled);
            all.addAll(changed);
            writeVersion(client, source, String.join(",", all), actor, otpVerified);
        }

        return new ApplyResult(client, filled, changed, blocked);
    }

    /** Called after the client proved the phone number with an OTP. */
    @Transactional
    public void markPhoneVerified(Client client) {
        client.setPhoneVerifiedAt(LocalDateTime.now());
        clientRepository.save(client);
    }

    // -------------------------------------------------------------------

    private boolean isSamePerson(Client c, ClientDetails d, int candidatesWithEmail) {

        // A different identity document means a different person.
        if (d.hasIdentity() && c.getIdentityNumberHash() != null) {
            String h = hashOf(d);
            return h.equals(c.getIdentityNumberHash());
        }

        String incoming = normalizeName(d.name());
        String stored = normalizeName(c.getName());

        if (!incoming.isEmpty() && !stored.isEmpty()) {
            return incoming.equals(stored);
        }
        // No name on one side: only safe if this is the only person on that email.
        return candidatesWithEmail == 1;
    }

    private interface Setter { void set(String value); }

    private void merge(
            String field, String current, String incoming, boolean otpVerified,
            Setter setter, List<String> filled, List<String> changed,
            List<String> blocked) {

        if (incoming == null) {
            return;
        }
        if (current == null || current.isBlank()) {
            setter.set(incoming);
            filled.add(field);
            return;
        }
        if (current.equalsIgnoreCase(incoming)) {
            return;
        }
        boolean sensitive = SENSITIVE_FIELDS.contains(field);
        if (!sensitive || otpVerified) {
            setter.set(incoming);
            changed.add(field);
        } else {
            blocked.add(field);
        }
    }

    private void setIdentity(Client c, ClientDetails d) {
        if (!d.hasIdentity()) {
            return;
        }
        c.setIdentityCountry(country(d));
        c.setIdentityType(type(d));
        c.setIdentityNumberEnc(identity.encrypt(d.identityNumber()));
        c.setIdentityNumberHash(hashOf(d));
        c.setIdentityNumberLast4(identity.last4(d.identityNumber()));
    }

    private void writeVersion(
            Client c, String source, String changedFields, String actor, boolean otp) {

        ClientVersion v = new ClientVersion();
        v.setClient(c);
        v.setVersionNo(c.getVersionNo());
        v.setName(c.getName());
        v.setEmail(c.getEmail());
        v.setPhone(c.getPhone());
        v.setDateOfBirth(c.getDateOfBirth());
        v.setGender(c.getGender());
        v.setNationality(c.getNationality());
        v.setCountryOfResidence(c.getCountryOfResidence());
        v.setIdentityCountry(c.getIdentityCountry());
        v.setIdentityType(c.getIdentityType());
        v.setIdentityNumberLast4(c.getIdentityNumberLast4());
        v.setChangeSource(source);
        v.setChangedFields(changedFields.length() > 500
                ? changedFields.substring(0, 500) : changedFields);
        v.setChangedBy(actor);
        v.setOtpVerified(otp);
        versionRepository.save(v);
    }

    private static String clean(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String normalizeName(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
