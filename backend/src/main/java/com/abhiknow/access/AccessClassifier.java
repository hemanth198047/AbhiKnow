package com.abhiknow.access;

import com.abhiknow.domain.AccessType;
import com.abhiknow.domain.Location;
import com.abhiknow.domain.Permission;

import java.util.List;
import java.util.Locale;

import static com.abhiknow.domain.Permission.NO;
import static com.abhiknow.domain.Permission.UNKNOWN;
import static com.abhiknow.domain.Permission.YES;

/**
 * Deterministic access classification. Rule: a resource is only labelled free/open when the provider
 * asserted it. No evidence means UNKNOWN. Free to read never implies redistributable.
 * Nothing here bypasses or probes paywalls; it only interprets provider metadata.
 */
public final class AccessClassifier {
    private AccessClassifier() {}

    public static boolean isOpenLicense(String license) {
        if (license == null) return false;
        String l = license.toLowerCase(Locale.ROOT);
        return l.contains("creativecommons.org") || l.startsWith("cc-") || l.startsWith("cc0") || l.equals("cc0")
                || l.contains("publicdomain") || l.equals("public-domain") || l.contains("opensource.org");
    }

    /** OpenAlex: open_access.is_oa, oa_status and best_oa_location license. */
    public static AccessEvidence openAlex(boolean isOa, String oaStatus, String license) {
        if (!isOa) {
            return new AccessEvidence(AccessType.UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN,
                    "OpenAlex does not list an open-access copy. Access depends on the publisher.");
        }
        boolean licensed = isOpenLicense(license);
        // "bronze" means free to read on the publisher site without an open license.
        if ("bronze".equalsIgnoreCase(oaStatus) && !licensed) {
            return new AccessEvidence(AccessType.FREE_TO_READ_ONLINE, YES, UNKNOWN, UNKNOWN,
                    "Free to read on the publisher site; no open license stated.");
        }
        if (licensed) {
            return new AccessEvidence(AccessType.OPEN_ACCESS, YES, YES, YES, "Open license: " + license);
        }
        return new AccessEvidence(AccessType.OPEN_ACCESS, YES, YES, UNKNOWN,
                "Listed as open access (" + (oaStatus == null ? "status unspecified" : oaStatus) + "); license not stated.");
    }

    /** Crossref: license URLs from the work's metadata. */
    public static AccessEvidence crossref(List<String> licenseUrls) {
        for (String l : licenseUrls) {
            if (isOpenLicense(l)) {
                return new AccessEvidence(AccessType.OPEN_ACCESS, YES, YES, YES, "Open license: " + l);
            }
        }
        return new AccessEvidence(AccessType.UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN,
                "No open license in Crossref metadata. Check the publisher for access.");
    }

    /** arXiv hosts the full text for free; the per-paper license is not in the API feed. */
    public static AccessEvidence arxiv() {
        return new AccessEvidence(AccessType.FREE_TO_READ_ONLINE, YES, YES, UNKNOWN,
                "Hosted by arXiv. Reuse terms depend on the license chosen by the authors.");
    }

    /** Open Library ebook_access: public, borrowable, printdisabled, no_ebook. */
    public static AccessEvidence openLibrary(String ebookAccess) {
        String v = ebookAccess == null ? "" : ebookAccess.toLowerCase(Locale.ROOT);
        return switch (v) {
            case "public" -> new AccessEvidence(AccessType.FREE_TO_READ_ONLINE, YES, UNKNOWN, UNKNOWN,
                    "Open Library lists a freely readable scan. Copyright status in your country is not verified.");
            case "borrowable" -> new AccessEvidence(AccessType.LIBRARY_BORROWING, UNKNOWN, NO, NO,
                    "Can be borrowed digitally (free account and loan limits apply).");
            case "printdisabled" -> new AccessEvidence(AccessType.RESTRICTED, NO, NO, NO,
                    "Available only to readers with print disabilities.");
            case "no_ebook" -> new AccessEvidence(AccessType.METADATA_ONLY, NO, NO, NO,
                    "Catalog record only; no digital copy listed.");
            default -> new AccessEvidence(AccessType.UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, "Access not stated.");
        };
    }

    /** Gutendex copyright flag: false = Project Gutenberg treats it as public domain in the US. */
    public static AccessEvidence gutenberg(Boolean copyright) {
        if (Boolean.FALSE.equals(copyright)) {
            return new AccessEvidence(AccessType.PUBLIC_DOMAIN, YES, YES, YES,
                    "Project Gutenberg: public domain in the United States. Rules differ in other countries.");
        }
        return new AccessEvidence(AccessType.UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN,
                "Copyright status not confirmed as public domain.");
    }

    /** Best access across all locations (best-first enum order). */
    public static AccessType best(List<Location> locations) {
        AccessType best = AccessType.UNKNOWN;
        for (Location l : locations) best = AccessType.best(best, l.accessType());
        return best;
    }

    /** Short recommended action for UI cards, derived from the best access type. */
    public static String recommendedAction(AccessType t) {
        return switch (t) {
            case PUBLIC_DOMAIN, OPEN_ACCESS, FREE_VERIFIED_PERMISSION, FREE_TO_READ_ONLINE -> "Read for free";
            case FREE_REGISTRATION -> "Register to read";
            case LIBRARY_BORROWING -> "Borrow from a library";
            case RESTRICTED -> "Access restricted";
            case METADATA_ONLY -> "View details";
            default -> "Check access at source";
        };
    }
}
