package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
public class MediaTimelineController {
    private final MediaTimelineService timelineService;

    public MediaTimelineController(MediaTimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping("/timeline")
    public R<MediaTimelineService.TimelinePage> timeline(
            Authentication auth,
            @RequestParam(required = false) Long spaceId,
            @RequestParam(defaultValue = "ALL") String mediaType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "60") int pageSize) {
        long userId = ((UserIdPrincipal) auth.getPrincipal()).userId();
        return R.ok(timelineService.listTimeline(userId, spaceId, mediaType, page, pageSize));
    }
}
