package com.mgh.backend.common.lookup.mapper;

import com.mgh.backend.common.lookup.dto.CityDto;
import com.mgh.backend.common.lookup.dto.GovernorateDto;
import com.mgh.backend.common.lookup.entity.City;
import com.mgh.backend.common.lookup.entity.Governorate;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-03T15:12:02+0300",
    comments = "version: 1.6.2, compiler: javac, environment: Java 21.0.10 (Microsoft)"
)
@Component
public class LookupMapperImpl implements LookupMapper {

    @Override
    public GovernorateDto toDto(Governorate governorate) {
        if ( governorate == null ) {
            return null;
        }

        GovernorateDto.GovernorateDtoBuilder governorateDto = GovernorateDto.builder();

        governorateDto.id( governorate.getId() );
        governorateDto.nameEn( governorate.getNameEn() );
        governorateDto.nameAr( governorate.getNameAr() );

        return governorateDto.build();
    }

    @Override
    public CityDto toDto(City city) {
        if ( city == null ) {
            return null;
        }

        CityDto.CityDtoBuilder cityDto = CityDto.builder();

        cityDto.id( city.getId() );
        cityDto.nameEn( city.getNameEn() );
        cityDto.nameAr( city.getNameAr() );

        return cityDto.build();
    }
}
