package com.abhiknow.access;

import com.abhiknow.domain.AccessType;
import com.abhiknow.domain.Permission;

/** Access classification derived only from what a provider asserted. */
public record AccessEvidence(AccessType type, Permission canRead, Permission canDownload,
                             Permission canRedistribute, String note) {
}
