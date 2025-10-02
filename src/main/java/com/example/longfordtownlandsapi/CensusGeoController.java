package com.example.longfordtownlandsapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@RestController
public class CensusGeoController {

    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping(value = "/census-geo", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getCensusGeo() throws Exception {

        // 1. Load the JSON file you downloaded from the API
        InputStream censusStream = new ClassPathResource("/data/census-records.json").getInputStream();
        JsonNode censusRoot = mapper.readTree(censusStream);
        ArrayNode results = (ArrayNode) censusRoot.get("results");

        // 2. Count people per townland
        Map<String, Long> counts = new HashMap<>();
        for (JsonNode record : results) {
            String townland = record.get("townland").asText().toUpperCase().trim();
            counts.put(townland, counts.getOrDefault(townland, 0L) + 1);
        }

        // 3. Load GeoJSON
        InputStream geoStream = new ClassPathResource("/data/Longford_Townlands.json").getInputStream();
        ObjectNode geojson = (ObjectNode) mapper.readTree(geoStream);
        ArrayNode features = (ArrayNode) geojson.get("features");

        // 4. Attach population counts
        for (JsonNode feature : features) {
            ObjectNode props = (ObjectNode) feature.get("properties");
            String name = props.get("ENG_NAME_VALUE").asText().toUpperCase().trim();
            long count = counts.getOrDefault(name, 0L);
            props.put("population_count", count);
        }

        return ResponseEntity.ok(mapper.writeValueAsString(geojson));
    }
}
