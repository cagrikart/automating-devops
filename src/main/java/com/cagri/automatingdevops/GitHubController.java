package com.cagri.automatingdevops;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @PostMapping("/merge/{githubRepo}/{pullNumber}")
    public ResponseEntity<String> mergePR(@PathVariable String githubRepo,@PathVariable int pullNumber,@RequestHeader("Authorization") String authorizationHeader) {
        try {
            String gitHubToken = authorizationHeader.replace("Bearer ", "");
            gitHubService.mergePullRequest(githubRepo,pullNumber,gitHubToken);
            return ResponseEntity.ok("PR merged successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to merge PR.");
        }
    }

    @PostMapping("/createTagAndRelease")
    public ResponseEntity<List<ReleaseResponse>> createTagAndRelease(@RequestHeader("Authorization") String authorizationHeader,@RequestBody ReleaseRequest releaseRequest) {
        try {
            String gitHubToken = authorizationHeader.replace("Bearer ", "");
            List<ReleaseResponse> response = gitHubService.createTagsAndReleases(releaseRequest.getTargetBranch()
                    , releaseRequest.getGitHubRepo(), releaseRequest.getCustomVersion()
                    , releaseRequest.getCrId(), releaseRequest.getDefectId(),gitHubToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


}
