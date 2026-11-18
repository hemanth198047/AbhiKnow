package com.abhiknow.resource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdsTest {
    @Test void doiNormalization() {
        assertThat(Ids.doi("https://doi.org/10.1000/ABC.def).")).isEqualTo("10.1000/abc.def");
        assertThat(Ids.doi("not a doi")).isNull();
    }

    @Test void isbnConversionAndValidation() {
        assertThat(Ids.isbn13("0306406152")).isEqualTo("9780306406157");
        assertThat(Ids.isbn13("097522980X")).isEqualTo("9780975229804");
        assertThat(Ids.isbn13("9780306406158")).isNull();
        assertThat(Ids.isbn13("12345")).isNull();
    }

    @Test void arxivVariants() {
        assertThat(Ids.arxiv("http://arxiv.org/abs/2105.04026v2")).isEqualTo("2105.04026");
        assertThat(Ids.arxiv("hep-th/9901001")).isEqualTo("hep-th/9901001");
        assertThat(Ids.arxiv("hello")).isNull();
        assertThat(Ids.arxivDoi("2105.04026")).isEqualTo("10.48550/arxiv.2105.04026");
    }

    @Test void onlyHttpUrlsSurvive() {
        assertThat(Ids.safeUrl("https://example.org/a")).isNotNull();
        assertThat(Ids.safeUrl("javascript:alert(1)")).isNull();
        assertThat(Ids.safeUrl("data:text/html;base64,AAAA")).isNull();
        assertThat(Ids.safeUrl("https://exa mple.org")).isNull();
    }

    @Test void textHelpers() {
        assertThat(Text.normalize("Café: Déjà-Vu!")).isEqualTo("cafe deja vu");
        assertThat(Text.displayName("Carroll, Lewis")).isEqualTo("Lewis Carroll");
        assertThat(Text.lastName("Lewis Carroll")).isEqualTo("carroll");
        assertThat(Text.lastName("Carroll, Lewis")).isEqualTo("carroll");
        assertThat(Text.stripMarkup("<p>a &amp; <i>b</i></p>")).isEqualTo("a & b");
        assertThat(Text.similarity("Deep Learning", "deep learning.")).isEqualTo(1.0);
    }
}
