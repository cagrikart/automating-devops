package com.cagri.automatingdevops;


import lombok.Data;


@Data
public class ReleaseResponse {
    private String targetBranch;
    private String tagName;
    private String releaseName;
    private String releaseTagUrl;
    private String releaseNotes;
}
