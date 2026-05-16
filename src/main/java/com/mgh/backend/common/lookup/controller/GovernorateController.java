package com.mgh.backend.common.lookup.controller;

import com.mgh.backend.common.lookup.dto.CityDto;
import com.mgh.backend.common.lookup.dto.GovernorateDto;
import com.mgh.backend.common.lookup.service.GovernorateLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/governorates")
@RequiredArgsConstructor
public class GovernorateController {

    private final GovernorateLookupService governorateLookupService;

    @GetMapping
    public ResponseEntity<Page<GovernorateDto>> getAllGovernorates(
            @PageableDefault(size = 50, sort = "nameEn", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(governorateLookupService.getAllGovernorates(pageable));
    }

    @GetMapping("/cities")
    public ResponseEntity<Page<CityDto>> getAllCities(
            @PageableDefault(size = 100, sort = "nameEn", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(governorateLookupService.getAllCities(pageable));
    }

    @GetMapping("/cities/search")
    public ResponseEntity<Page<CityDto>> searchCities(
            @RequestParam("name") String name,
            @PageableDefault(size = 50, sort = "nameEn", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(governorateLookupService.searchCitiesByName(name, pageable));
    }

    @GetMapping("/{id}/cities")
    public ResponseEntity<Page<CityDto>> getCitiesByGovernorate(
            @PathVariable Long id,
            @PageableDefault(size = 100, sort = "nameEn", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(governorateLookupService.getCitiesByGovernorateId(id, pageable));
    }
}
