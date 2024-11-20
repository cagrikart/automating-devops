package com.cagri.automatingdevops;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @PostMapping("/merge/{pullNumber}")
    public ResponseEntity<String> mergePR(@PathVariable int pullNumber) {
        try {
            gitHubService.mergePullRequest(pullNumber);
            return ResponseEntity.ok("PR merged successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to merge PR.");
        }
    }

    @PostMapping("/createReleaseWithTargetBranch")
    public ResponseEntity<String> createReleaseWithTargetBranch(@RequestBody Map<String, String> body) {
        try {
            String tagName = body.get("tagName");
            String releaseName = body.get("releaseName");
            String releaseBody = body.get("releaseBody");
            String targetBranch = body.get("targetBranch");
            gitHubService.createReleaseWithTargetBranch(tagName, releaseName,releaseBody,targetBranch);
            return ResponseEntity.ok("Tag created successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create tag.");
        }
    }

}
