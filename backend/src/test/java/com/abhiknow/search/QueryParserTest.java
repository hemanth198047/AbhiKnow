package com.abhiknow.search;

import org.junit.jupiter.api.Test;

import static com.abhiknow.search.ParsedQuery.Mode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryParserTest {
    private final QueryParser p = new QueryParser();

    @Test void acceptanceQueryIsReducedToKeywords() {
        ParsedQuery q = p.parse("Artificial Intelligence from beginner to advanced research.");
        assertThat(q.mode()).isEqualTo(KEYWORD);
        assertThat(q.text()).isEqualTo("Artificial Intelligence");
        assertThat(q.raw()).isEqualTo("Artificial Intelligence from beginner to advanced research.");
    }

    @Test void conversationalPrefixIsRemoved() {
        assertThat(p.parse("I want to become an expert in Artificial Intelligence").text()).isEqualTo("Artificial Intelligence");
        assertThat(p.parse("learn quantum computing").text()).isEqualTo("quantum computing");
    }

    @Test void plainKeywordsAreUntouched() {
        assertThat(p.parse("graph neural networks").text()).isEqualTo("graph neural networks");
    }

    @Test void doiInBareAndUrlForm() {
        assertThat(p.parse("10.1038/nature14539").mode()).isEqualTo(DOI);
        ParsedQuery q = p.parse("https://doi.org/10.1038/NATURE14539");
        assertThat(q.mode()).isEqualTo(DOI);
        assertThat(q.doi()).isEqualTo("10.1038/nature14539");
    }

    @Test void isbn10And13WithHyphens() {
        assertThat(p.parse("0-306-40615-2").isbn()).isEqualTo("9780306406157");
        assertThat(p.parse("ISBN 978-0-306-40615-7").mode()).isEqualTo(ISBN);
    }

    @Test void invalidIsbnChecksumIsTreatedAsKeywords() {
        assertThat(p.parse("978-0-306-40615-8").mode()).isEqualTo(KEYWORD);
    }

    @Test void arxivId() {
        ParsedQuery q = p.parse("arXiv:1706.03762v5");
        assertThat(q.mode()).isEqualTo(ARXIV);
        assertThat(q.arxivId()).isEqualTo("1706.03762");
    }

    @Test void authorQueries() {
        ParsedQuery q = p.parse("author:\"Geoffrey Hinton\" backpropagation");
        assertThat(q.mode()).isEqualTo(AUTHOR);
        assertThat(q.author()).isEqualTo("Geoffrey Hinton");
        assertThat(q.text()).isEqualTo("backpropagation");
    }

    @Test void quotedPhrasesAreKeptAndNotStripped() {
        ParsedQuery q = p.parse("learn \"attention is all you need\"");
        assertThat(q.phrases()).containsExactly("attention is all you need");
        assertThat(q.text()).contains("learn");
    }

    @Test void blankIsRejected() {
        assertThatThrownBy(() -> p.parse("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
