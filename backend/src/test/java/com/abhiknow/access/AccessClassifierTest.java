package com.abhiknow.access;

import com.abhiknow.domain.AccessType;
import com.abhiknow.domain.Permission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessClassifierTest {
    @Test void openAlexClosedIsUnknownNotPaid() {
        AccessEvidence e = AccessClassifier.openAlex(false, "closed", null);
        assertThat(e.type()).isEqualTo(AccessType.UNKNOWN);
        assertThat(e.canRead()).isEqualTo(Permission.UNKNOWN);
    }

    @Test void openAlexLicensedOpenAccessAllowsRedistribution() {
        AccessEvidence e = AccessClassifier.openAlex(true, "gold", "cc-by");
        assertThat(e.type()).isEqualTo(AccessType.OPEN_ACCESS);
        assertThat(e.canRedistribute()).isEqualTo(Permission.YES);
    }

    @Test void bronzeWithoutLicenseIsFreeToReadButNotRedistributable() {
        AccessEvidence e = AccessClassifier.openAlex(true, "bronze", null);
        assertThat(e.type()).isEqualTo(AccessType.FREE_TO_READ_ONLINE);
        assertThat(e.canRedistribute()).isNotEqualTo(Permission.YES);
    }

    @Test void crossrefOnlyOpenLicensesCount() {
        assertThat(AccessClassifier.crossref(List.of("https://www.springer.com/tdm")).type()).isEqualTo(AccessType.UNKNOWN);
        assertThat(AccessClassifier.crossref(List.of("https://creativecommons.org/licenses/by/4.0/")).type()).isEqualTo(AccessType.OPEN_ACCESS);
    }

    @Test void openLibraryAccessValues() {
        assertThat(AccessClassifier.openLibrary("public").type()).isEqualTo(AccessType.FREE_TO_READ_ONLINE);
        assertThat(AccessClassifier.openLibrary("borrowable").type()).isEqualTo(AccessType.LIBRARY_BORROWING);
        assertThat(AccessClassifier.openLibrary("borrowable").canDownload()).isEqualTo(Permission.NO);
        assertThat(AccessClassifier.openLibrary("printdisabled").type()).isEqualTo(AccessType.RESTRICTED);
        assertThat(AccessClassifier.openLibrary("no_ebook").type()).isEqualTo(AccessType.METADATA_ONLY);
        assertThat(AccessClassifier.openLibrary(null).type()).isEqualTo(AccessType.UNKNOWN);
    }

    @Test void gutenbergOnlyFalseCopyrightIsPublicDomain() {
        assertThat(AccessClassifier.gutenberg(false).type()).isEqualTo(AccessType.PUBLIC_DOMAIN);
        assertThat(AccessClassifier.gutenberg(true).type()).isEqualTo(AccessType.UNKNOWN);
        assertThat(AccessClassifier.gutenberg(null).type()).isEqualTo(AccessType.UNKNOWN);
    }

    @Test void freeNeverAssumedFromUnknown() {
        assertThat(AccessType.UNKNOWN.isFree()).isFalse();
        assertThat(AccessType.LIBRARY_BORROWING.isFree()).isFalse();
        assertThat(AccessType.PUBLIC_DOMAIN.isFree()).isTrue();
    }
}
