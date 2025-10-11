package com.example.longfordtownlandsapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class CensusGeoController {

    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping(value = "/census-geo", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getCensusGeo() throws Exception {

        // 1. Load the JSON file you downloaded from the API
        InputStream censusStream = new ClassPathResource("/data/census-records.json").getInputStream();
        JsonNode censusRoot = mapper.readTree(censusStream);
        ArrayNode results = (ArrayNode) censusRoot.get("results");

        // 2. Count people per townland+DED combination
        Map<String, Long> counts = new HashMap<>();
        for (JsonNode record : results) {
            String townland = record.get("townland").asText().toUpperCase().trim();
            String ded = record.get("ded").asText();
            String key = townland + "|" + ded;
            counts.put(key, counts.getOrDefault(key, 0L) + 1);
        }

        // 3. Load DED boundaries
        InputStream dedStream = new ClassPathResource("/data/Census_1911_DED_generalised20m.json").getInputStream();
        JsonNode dedGeoJson = mapper.readTree(dedStream);
        ArrayNode dedFeatures = (ArrayNode) dedGeoJson.get("features");

        // 4. Load townlands GeoJSON
        InputStream geoStream = new ClassPathResource("/data/Longford_Townlands.json").getInputStream();
        ObjectNode geojson = (ObjectNode) mapper.readTree(geoStream);
        ArrayNode features = (ArrayNode) geojson.get("features");

        // 5. Attach population counts and find DED using spatial lookup
        for (JsonNode feature : features) {
            ObjectNode props = (ObjectNode) feature.get("properties");
            String name = props.get("ENG_NAME_VALUE").asText().toUpperCase().trim();
            
            // Get townland centroid
            double[] centroid = getCentroid(feature.get("geometry"));
            String ded = findDEDForPoint(centroid[0], centroid[1], dedFeatures);
            
            // Create key matching census data format
            String key = name + "|" + ded;
            long count = counts.getOrDefault(key, 0L);
            
            props.put("population_count", count);
            props.put("ded", ded);
        }

        return ResponseEntity.ok(mapper.writeValueAsString(geojson));
    }

    private double[] getCentroid(JsonNode geometry) {
        ArrayNode coordinates = (ArrayNode) geometry.get("coordinates").get(0);
        double sumLon = 0, sumLat = 0;
        int count = 0;
        for (JsonNode coord : coordinates) {
            sumLon += coord.get(0).asDouble();
            sumLat += coord.get(1).asDouble();
            count++;
        }
        return new double[]{sumLon / count, sumLat / count};
    }

    private String findDEDForPoint(double lon, double lat, ArrayNode dedFeatures) {
        for (JsonNode dedFeature : dedFeatures) {
            if (isPointInPolygon(lon, lat, dedFeature.get("geometry"))) {
                return dedFeature.get("properties").get("geolabel").asText();
            }
        }
        return "UNKNOWN";
    }

    private boolean isPointInPolygon(double lon, double lat, JsonNode geometry) {
        ArrayNode coordinates = (ArrayNode) geometry.get("coordinates").get(0);
        int intersections = 0;
        for (int i = 0; i < coordinates.size() - 1; i++) {
            double x1 = coordinates.get(i).get(0).asDouble();
            double y1 = coordinates.get(i).get(1).asDouble();
            double x2 = coordinates.get(i + 1).get(0).asDouble();
            double y2 = coordinates.get(i + 1).get(1).asDouble();
            
            if (((y1 > lat) != (y2 > lat)) && (lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1)) {
                intersections++;
            }
        }
        return intersections % 2 == 1;
    }
}
