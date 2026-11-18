package com.abhiknow.domain;

/**
 * Access taxonomy (REQUIREMENTS F-06). Availability, price and permissions are tracked separately
 * on {@link Location}; this enum only says how a user can get to the content.
 * Declared best-first: ordinal order is used to pick the best access across locations.
 */
public enum AccessType {
    PUBLIC_DOMAIN(true, "Public domain"),
    OPEN_ACCESS(true, "Open access"),
    FREE_VERIFIED_PERMISSION(true, "Free (permission verified)"),
    FREE_TO_READ_ONLINE(true, "Free to read online"),
    FREE_REGISTRATION(true, "Free, registration required"),
    LIBRARY_BORROWING(false, "Library borrowing"),
    USER_OWNED(false, "Your own content"),
    CAPTCHA_REQUIRED(false, "Human verification required"),
    WAITING_PERIOD(false, "Waiting period required"),
    AUTHENTICATION_REQUIRED(false, "Sign-in required"),
    INSTITUTIONAL(false, "Institutional access"),
    SUBSCRIPTION(false, "Subscription required"),
    PURCHASE(false, "Purchase required"),
    RENTAL(false, "Rental"),
    METADATA_ONLY(false, "Metadata only"),
    RESTRICTED(false, "Restricted"),
    UNKNOWN(false, "Access unknown");

    private final boolean free;
    private final String label;

    AccessType(boolean free, String label) {
        this.free = free;
        this.label = label;
    }

    /** True only when a provider asserted that the content can be read without payment. */
    public boolean isFree() { return free; }

    public String label() { return label; }

    public static AccessType best(AccessType a, AccessType b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
