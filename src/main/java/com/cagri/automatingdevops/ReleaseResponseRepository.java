package com.cagri.automatingdevops;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReleaseResponseRepository extends MongoRepository<ReleaseResponse, String> {
}
