package com.abhiknow.api;

import com.abhiknow.domain.ResourceType;
import com.abhiknow.provider.ProviderRuntime;
import com.abhiknow.search.ResourceDto;
import com.abhiknow.search.SearchRequest;
import com.abhiknow.search.SearchResponse;
import com.abhiknow.search.SearchService;
import com.abhiknow.store.ResourceStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api")
public class SearchController {
    private final SearchService search;
    private final ResourceStore store;
    private final ProviderRuntime runtime;

    public SearchController(SearchService search, ResourceStore store, ProviderRuntime runtime) {
        this.search = search;
        this.store = store;
        this.runtime = runtime;
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam String q,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(required = false) List<String> types,
                                 @RequestParam(required = false) Integer yearFrom,
                                 @RequestParam(required = false) Integer yearTo,
                                 @RequestParam(required = false) String language,
                                 @RequestParam(defaultValue = "ANY") String access,
                                 @RequestParam(required = false) List<String> providers) {
        if (q == null || q.isBlank()) throw new IllegalArgumentException("Query must not be empty");
        if (q.length() > 300) throw new IllegalArgumentException("Query is too long (max 300 characters)");
        if (page < 1 || page > 20) throw new IllegalArgumentException("page must be between 1 and 20");
        if (yearFrom != null && yearTo != null && yearFrom > yearTo) throw new IllegalArgumentException("yearFrom must not exceed yearTo");
        Set<ResourceType> typeSet = new LinkedHashSet<>();
        if (types != null) {
            for (String t : types) {
                try {
                    typeSet.add(ResourceType.valueOf(t.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown resource type: " + t);
                }
            }
        }
        SearchRequest.AccessFilter af;
        try {
            af = SearchRequest.AccessFilter.valueOf(access.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("access must be one of ANY, FREE, OPEN");
        }
        Set<String> providerSet = providers == null ? Set.of() : new LinkedHashSet<>(providers);
        return search.search(new SearchRequest(q.trim(), page, typeSet, yearFrom, yearTo, language, af, providerSet));
    }

    @GetMapping("/resources/{id}")
    public ResourceDto resource(@PathVariable UUID id) {
        return store.find(id).map(ResourceDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
    }

    public record ProviderView(String id, String name, String baseUrl, String trustStatus, boolean automationAllowed,
                               String lastReviewed, String notes, boolean pausedByCircuitBreaker) {}

    @GetMapping("/providers")
    public List<ProviderView> providers() {
        return store.providers().stream().map(p -> new ProviderView(p.id(), p.displayName(), p.baseUrl(), p.trustStatus(),
                p.automationAllowed(), p.lastReviewed(), p.notes(), runtime.isCircuitOpen(p.id()))).toList();
    }
}
