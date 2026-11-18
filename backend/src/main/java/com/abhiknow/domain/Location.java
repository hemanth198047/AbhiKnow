package com.abhiknow.domain;

/**
 * One place a resource can be reached, with the access evidence the provider supplied.
 * canRead / canDownload / canRedistribute are independent: free to read does not imply redistributable.
 */
public record Location(
        String providerId,
        String url,
        String kind,          // LANDING, PDF, EPUB, HTML, TXT, READ_ONLINE, BORROW
        String format,
        AccessType accessType,
        String license,
        Permission canRead,
        Permission canDownload,
        Permission canRedistribute,
        String note) {
}
