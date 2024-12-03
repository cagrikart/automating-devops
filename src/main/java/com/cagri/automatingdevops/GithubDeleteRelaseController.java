package com.cagri.automatingdevops;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/githubDelete")
public class GithubDeleteRelaseController {

    @Value("${github.token}")
    private String githubToken;

    private static final String BASE_URL = "https://api.github.com/repos";

    @DeleteMapping("/deleteTagsAndReleases")
    public ResponseEntity<String> deleteTagsAndReleases(@RequestBody Map<String, String> request) {
        String owner = request.get("owner");
        String repo = request.get("repo");

        if (owner == null || repo == null) {
            return ResponseEntity.badRequest().body("Owner and repository are required.");
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github+json");

        try {
            // Delete tags
            String tagsUrl = BASE_URL + "/" + owner + "/" + repo + "/tags";
            ResponseEntity<String> tagsResponse = restTemplate.exchange(tagsUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (tagsResponse.getStatusCode().is2xxSuccessful()) {
                String tagsBody = tagsResponse.getBody();
                if (tagsBody != null) {
                    // Extract tag names and delete them
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode tags = objectMapper.readTree(tagsBody);

                    for (JsonNode tag : tags) {
                        String tagName = tag.get("name").asText();
                        String deleteTagUrl = BASE_URL + "/" + owner + "/" + repo + "/git/refs/tags/" + tagName;
                        restTemplate.exchange(deleteTagUrl, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
                        System.out.println("Deleted tag: " + tagName);
                    }
                }
            }

            // Delete releases
            String releasesUrl = BASE_URL + "/" + owner + "/" + repo + "/releases";
            ResponseEntity<String> releasesResponse = restTemplate.exchange(releasesUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (releasesResponse.getStatusCode().is2xxSuccessful()) {
                String releasesBody = releasesResponse.getBody();
                if (releasesBody != null) {
                    // Extract release IDs and delete them
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode releases = objectMapper.readTree(releasesBody);

                    for (JsonNode release : releases) {
                        String releaseId = release.get("id").asText();
                        String deleteReleaseUrl = BASE_URL + "/" + owner + "/" + repo + "/releases/" + releaseId;
                        restTemplate.exchange(deleteReleaseUrl, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
                        System.out.println("Deleted release: " + release.get("name").asText());
                    }
                }
            }

            return ResponseEntity.ok("Tags and releases deleted successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete tags and releases: " + e.getMessage());
        }
    }
}
