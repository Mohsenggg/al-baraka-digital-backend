package com.mgh.backend.common.lookup.mapper;

import com.mgh.backend.common.lookup.dto.CityDto;
import com.mgh.backend.common.lookup.dto.GovernorateDto;
import com.mgh.backend.common.lookup.entity.City;
import com.mgh.backend.common.lookup.entity.Governorate;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LookupMapper {

    GovernorateDto toDto(Governorate governorate);
    CityDto toDto(City city);
}
