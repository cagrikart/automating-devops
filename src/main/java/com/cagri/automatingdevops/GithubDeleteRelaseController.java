package com.cagri.automatingdevops;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


@RestController
@RequestMapping("/githubDelete")
public class GithubDeleteRelaseController {



  private static final String BASE_URL = "https://api.github.com/repos";

    private HttpHeaders createHeaders(String gitHubToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");
        return headers;
    }
    @DeleteMapping("/deleteTagsAndReleases")
    public ResponseEntity<String> deleteTagsAndReleases(@RequestHeader("Authorization") String authorizationHeader,@RequestBody Map<String, Object> request) {
        String owner = (String) request.get("owner");
        String repo = (String) request.get("repo");
        Integer limit = (Integer) request.get("limit");

        if (owner == null || repo == null || limit == null) {
            return ResponseEntity.badRequest().body("Owner, repository, and limit are required.");
        }
        String gitHubToken = authorizationHeader.replace("Bearer ", "");
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + gitHubToken);
        headers.set("Accept", "application/vnd.github+json");

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            StringBuilder responseMessage = new StringBuilder();

            // Release ve Tag bilgilerini birleştirecek liste
            List<JsonNode> combinedList = new ArrayList<>();

            // Release'leri Al
            String releasesUrl = BASE_URL + "/" + owner + "/" + repo + "/releases";
            ResponseEntity<String> releasesResponse = restTemplate.exchange(releasesUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (releasesResponse.getStatusCode().is2xxSuccessful()) {
                String releasesBody = releasesResponse.getBody();
                if (releasesBody != null) {
                    JsonNode releases = objectMapper.readTree(releasesBody);
                    for (JsonNode release : releases) {
                        if (release.has("id") && release.has("name") && release.has("created_at")) {
                            // Release'i listeye ekle
                            ((ObjectNode) release).put("type", "release");
                            combinedList.add(release);
                        }
                    }
                }
            }

            // Tag'leri Al
            String tagsUrl = BASE_URL + "/" + owner + "/" + repo + "/git/refs/tags";
            ResponseEntity<String> tagsResponse = restTemplate.exchange(tagsUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (tagsResponse.getStatusCode().is2xxSuccessful()) {
                String tagsBody = tagsResponse.getBody();
                if (tagsBody != null) {
                    JsonNode tags = objectMapper.readTree(tagsBody);
                    for (JsonNode tag : tags) {
                        if (tag.has("ref")) {
                            String tagName = tag.get("ref").asText().substring("refs/tags/".length());
                            // Tag'lere tarih bilgisi eklenmediği için varsayılan bir değer oluşturulabilir
                            JsonNode tagNode = objectMapper.createObjectNode()
                                    .put("type", "tag")
                                    .put("name", tagName)
                                    .put("created_at", "0000-00-00T00:00:00Z") // Varsayılan tarih
                                    .set("tag_info", tag);
                            combinedList.add(tagNode);
                        }
                    }
                }
            }

            // Listeyi Tarihe Göre Sırala
            combinedList.sort(Comparator.comparing(item -> item.get("created_at").asText()));

            // Limit kadar işlem yap
            int count = 0;
            for (JsonNode item : combinedList) {
                if (count >= limit) break;

                String type = item.get("type").asText();
                if ("release".equals(type)) {
                    // Release Sil
                    String releaseId = item.get("id").asText();
                    String deleteReleaseUrl = BASE_URL + "/" + owner + "/" + repo + "/releases/" + releaseId;
                    restTemplate.exchange(deleteReleaseUrl, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
                    responseMessage.append("Deleted release: ").append(item.get("name").asText()).append("\n");
                } else if ("tag".equals(type)) {
                    // Tag Sil
                    String tagName = item.get("name").asText();
                    String deleteTagUrl = BASE_URL + "/" + owner + "/" + repo + "/git/refs/tags/" + tagName;
                    restTemplate.exchange(deleteTagUrl, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
                    responseMessage.append("Deleted tag: ").append(tagName).append("\n");
                }
                count++;
            }

            return ResponseEntity.ok(responseMessage.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete tags and releases: " + e.getMessage());
        }
    }


}
