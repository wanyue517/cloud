package com.wanyue.cloud.modules.drive.search;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import com.wanyue.cloud.modules.drive.search.SearchModels.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SearchController {
    private final SearchQueryService queryService;
    private final SearchIndexService indexService;

    public SearchController(SearchQueryService queryService, SearchIndexService indexService) {
        this.queryService = queryService;
        this.indexService = indexService;
    }

    private static long user(Authentication auth) {
        return ((UserIdPrincipal) auth.getPrincipal()).userId();
    }

    @GetMapping("/search")
    public R<SearchResponse> search(
            Authentication auth,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String extension,
            @RequestParam(defaultValue = "true") boolean includeContent,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(queryService.search(user(auth), query, spaceId, category, extension, includeContent, page, pageSize));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/admin/search/status")
    public R<IndexStatus> status() {
        return R.ok(indexService.status());
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/admin/search/rebuild")
    public R<Map<String, Object>> rebuild() {
        int indexed = indexService.rebuildAll();
        return R.ok(Map.of("indexed", indexed));
    }
}
