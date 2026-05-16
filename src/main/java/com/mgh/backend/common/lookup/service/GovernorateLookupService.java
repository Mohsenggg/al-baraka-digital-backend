package com.mgh.backend.common.lookup.service;

import com.mgh.backend.common.lookup.dto.CityDto;
import com.mgh.backend.common.lookup.dto.GovernorateDto;
import com.mgh.backend.common.lookup.entity.City;
import com.mgh.backend.common.lookup.entity.Governorate;
import com.mgh.backend.common.lookup.mapper.LookupMapper;
import com.mgh.backend.common.lookup.repository.CityRepository;
import com.mgh.backend.common.lookup.repository.GovernorateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GovernorateLookupService {

    private final GovernorateRepository governorateRepository;
    private final CityRepository cityRepository;
    private final LookupMapper lookupMapper;

    public Page<GovernorateDto> getAllGovernorates(Pageable pageable) {
        Page<Governorate> page = governorateRepository.findAll(pageable);
        Page<GovernorateDto> governorateDtos = page.map(lookupMapper::toDto);
        return governorateDtos;

    }

    @Cacheable(
            cacheNames = "citiesByGovernorate",
            key = "#governorateId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()"
    )
    public Page<CityDto> getCitiesByGovernorateId(Long governorateId, Pageable pageable) {
        if (!governorateRepository.existsById(governorateId)) {
            throw new EntityNotFoundException("Governorate not found with id: " + governorateId);
        }
        Page<City> page = cityRepository.findByGovernorateId(governorateId, pageable);
        return page.map(lookupMapper::toDto);
    }

    public Page<CityDto> getAllCities(Pageable pageable) {
        Page<City> page = cityRepository.findAll(pageable);
        return page.map(lookupMapper::toDto);
    }

    public Page<CityDto> searchCitiesByName(String name, Pageable pageable) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Search name must not be blank");
        }
        String trimmed = name.trim();
        Page<City> page = cityRepository.searchByName(trimmed, pageable);
        return page.map(lookupMapper::toDto);
    }
}
