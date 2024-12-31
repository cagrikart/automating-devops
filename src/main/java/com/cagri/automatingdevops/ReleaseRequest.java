package com.cagri.automatingdevops;


import lombok.Data;

import java.util.List;


@Data
public class ReleaseRequest {
    private String targetBranch;
    private List<String> gitHubRepo;
    private String customVersion;
    private String crId;
    private String defectId;
}
