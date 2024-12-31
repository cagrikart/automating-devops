package com.cagri.automatingdevops;


import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;


@Data
@Document(collection = "releaseResponses")
public class ReleaseResponse {
    @Id
    private String id;
    private String targetBranch;
    private String tagName;
    private String releaseName;
    private String releaseTagUrl;
    private String releaseNotes;
    private String developerFullName;
    private String gitHubRepo;
    private String date;
    private String crId;
    private String defectId;
}
