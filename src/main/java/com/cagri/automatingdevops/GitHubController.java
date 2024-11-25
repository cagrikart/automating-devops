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

    @PostMapping("/merge/{githubRepo}/{pullNumber}")
    public ResponseEntity<String> mergePR(@PathVariable String githubRepo,@PathVariable int pullNumber) {
        try {
            gitHubService.mergePullRequest(githubRepo,pullNumber);
            return ResponseEntity.ok("PR merged successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to merge PR.");
        }
    }

    @PostMapping("/createTagAndRelease")
    public ResponseEntity<ReleaseResponse> createTagAndRelease(@RequestBody Map<String, String> body) {
        try {
            String targetBranch = body.get("targetBranch");
            ReleaseResponse response = gitHubService.createTagAndRelease(targetBranch);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


}
